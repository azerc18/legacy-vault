package com.ltld.app.legacyvault.service.messageservice;

import com.ltld.app.legacyvault.dto.messagedto.CreateLegacyMessageRequest;
import com.ltld.app.legacyvault.dto.messagedto.LegacyMessageResponse;
import com.ltld.app.legacyvault.dto.messagedto.UpdateLegacyMessageRequest;
import com.ltld.app.legacyvault.entity.LegacyMessage;
import com.ltld.app.legacyvault.entity.Notification;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.entity.VaultBeneficiary;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.BeneficiaryInviteStatus;
import com.ltld.app.legacyvault.enums.LegacyMessageDeliveryTrigger;
import com.ltld.app.legacyvault.enums.LegacyMessageRecipientType;
import com.ltld.app.legacyvault.enums.LegacyMessageStatus;
import com.ltld.app.legacyvault.enums.NotificationChannel;
import com.ltld.app.legacyvault.enums.NotificationStatus;
import com.ltld.app.legacyvault.enums.NotificationType;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.enums.VerificationStatus;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.repository.IdentityVerificationRepository;
import com.ltld.app.legacyvault.repository.LegacyMessageRepository;
import com.ltld.app.legacyvault.repository.NotificationRepository;
import com.ltld.app.legacyvault.repository.VaultBeneficiaryRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import com.ltld.app.legacyvault.service.fileservice.FileStorageService;
import com.ltld.app.legacyvault.service.paymentservice.EntitlementService;
import com.ltld.app.legacyvault.service.vaultservice.VaultModificationGuard;
import com.ltld.app.legacyvault.utility.EmailSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class LegacyMessageServiceImpl implements LegacyMessageService {

    private static final long MAX_ATTACHMENT_SIZE = 10 * 1024 * 1024L; // 10MB
    private static final Set<VaultStatus> HANDOVER_VAULT_STATUSES = Set.of(VaultStatus.UNLOCKED, VaultStatus.CLAIMED);
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int VIEW_SESSION_MINUTES = 30;

    private final LegacyMessageRepository legacyMessageRepository;
    private final VaultRepository vaultRepository;
    private final VaultBeneficiaryRepository vaultBeneficiaryRepository;
    private final IdentityVerificationRepository identityVerificationRepository;
    private final CryptoService cryptoService;
    private final FileStorageService fileStorageService;
    private final EntitlementService entitlementService;
    private final VaultModificationGuard vaultModificationGuard;
    private final AuditLogService auditLogService;
    private final NotificationRepository notificationRepository;
    private final EmailSender emailSender;
    private final TransactionTemplate requiresNewTx;

    @Autowired
    public LegacyMessageServiceImpl(
            LegacyMessageRepository legacyMessageRepository,
            VaultRepository vaultRepository,
            VaultBeneficiaryRepository vaultBeneficiaryRepository,
            IdentityVerificationRepository identityVerificationRepository,
            CryptoService cryptoService,
            FileStorageService fileStorageService,
            EntitlementService entitlementService,
            VaultModificationGuard vaultModificationGuard,
            AuditLogService auditLogService,
            NotificationRepository notificationRepository,
            EmailSender emailSender,
            PlatformTransactionManager transactionManager) {
        this.legacyMessageRepository = legacyMessageRepository;
        this.vaultRepository = vaultRepository;
        this.vaultBeneficiaryRepository = vaultBeneficiaryRepository;
        this.identityVerificationRepository = identityVerificationRepository;
        this.cryptoService = cryptoService;
        this.fileStorageService = fileStorageService;
        this.entitlementService = entitlementService;
        this.vaultModificationGuard = vaultModificationGuard;
        this.auditLogService = auditLogService;
        this.notificationRepository = notificationRepository;
        this.emailSender = emailSender;
        this.requiresNewTx = new TransactionTemplate(transactionManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    @Transactional
    public LegacyMessageResponse createMessage(UUID ownerId, UUID vaultId, CreateLegacyMessageRequest request, MultipartFile attachment) throws Exception {
        Vault vault = getVaultForOwner(vaultId, ownerId);
        vaultModificationGuard.ensureModifiable(vault);

        entitlementService.assertCanUseLegacyMessages(ownerId);

        validateDeliveryTrigger(request.getDeliveryTrigger(), request.getDeliverDate());

        VaultBeneficiary recipientBeneficiary = null;
        if (request.getRecipientType() == LegacyMessageRecipientType.SPECIFIC_BENEFICIARY) {
            if (request.getRecipientBeneficiaryId() == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "recipientBeneficiaryId is required when recipientType is SPECIFIC_BENEFICIARY");
            }
            recipientBeneficiary = vaultBeneficiaryRepository.findById(request.getRecipientBeneficiaryId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Beneficiary not found"));
            if (!recipientBeneficiary.getVault().getId().equals(vaultId)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Beneficiary does not belong to this vault");
            }
        }

        String attachmentPath = null;
        String attachmentName = null;
        String attachmentType = null;
        Long attachmentSize = null;

        if (attachment != null && !attachment.isEmpty()) {
            if (attachment.getSize() > MAX_ATTACHMENT_SIZE) {
                throw new BusinessException(ErrorCode.FILE_TOO_LARGE, "Attachment size exceeds 10MB limit");
            }

            entitlementService.assertStorageQuota(ownerId, attachment.getSize());

            attachmentName = attachment.getOriginalFilename();
            attachmentType = attachment.getContentType();
            attachmentSize = attachment.getSize();

            byte[] cipherBytes = cryptoService.encryptBytes(attachment.getBytes());
            attachmentPath = fileStorageService.storeFile(attachment, cipherBytes);
        }

        String encryptedContent = cryptoService.encrypt(request.getContent());

        LegacyMessage message = LegacyMessage.builder()
                .vault(vault)
                .recipientType(request.getRecipientType())
                .recipientBeneficiary(recipientBeneficiary)
                .title(request.getTitle())
                .content(encryptedContent)
                .deliveryTrigger(request.getDeliveryTrigger())
                .deliverDate(request.getDeliveryTrigger() == LegacyMessageDeliveryTrigger.SPECIFIC_DATE ? request.getDeliverDate() : null)
                .status(LegacyMessageStatus.ACTIVE)
                .attachmentPath(attachmentPath)
                .attachmentName(attachmentName)
                .attachmentType(attachmentType)
                .attachmentSize(attachmentSize)
                .build();

        LegacyMessage saved = legacyMessageRepository.save(message);

        auditLogService.log(AuditAction.VAULT_UPDATED, AuditResult.SUCCESS, ownerId, null,
                vaultId, "Vault", vaultId.toString(), "Created legacy message: " + saved.getTitle());

        return toResponse(saved, request.getContent(), true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LegacyMessageResponse> getMessagesByVault(UUID ownerId, UUID vaultId) {
        getVaultForOwner(vaultId, ownerId);
        List<LegacyMessage> messages = legacyMessageRepository.findByVaultIdOrderByCreatedAtDesc(vaultId);

        List<LegacyMessageResponse> responses = new ArrayList<>();
        for (LegacyMessage m : messages) {
            String decryptedContent = decryptContentSafely(m.getContent());
            responses.add(toResponse(m, decryptedContent, true));
        }
        return responses;
    }

    @Override
    @Transactional(readOnly = true)
    public LegacyMessageResponse getMessageById(UUID ownerId, UUID vaultId, UUID messageId) throws Exception {
        getVaultForOwner(vaultId, ownerId);
        LegacyMessage message = legacyMessageRepository.findByIdAndVaultId(messageId, vaultId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Legacy message not found"));

        String decryptedContent = cryptoService.decrypt(message.getContent());
        return toResponse(message, decryptedContent, true);
    }

    @Override
    @Transactional
    public LegacyMessageResponse updateMessage(UUID ownerId, UUID vaultId, UUID messageId, UpdateLegacyMessageRequest request, MultipartFile attachment) throws Exception {
        Vault vault = getVaultForOwner(vaultId, ownerId);
        vaultModificationGuard.ensureModifiable(vault);

        LegacyMessage message = legacyMessageRepository.findByIdAndVaultId(messageId, vaultId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Legacy message not found"));

        validateDeliveryTrigger(request.getDeliveryTrigger(), request.getDeliverDate());

        VaultBeneficiary recipientBeneficiary = null;
        if (request.getRecipientType() == LegacyMessageRecipientType.SPECIFIC_BENEFICIARY) {
            if (request.getRecipientBeneficiaryId() == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "recipientBeneficiaryId is required when recipientType is SPECIFIC_BENEFICIARY");
            }
            recipientBeneficiary = vaultBeneficiaryRepository.findById(request.getRecipientBeneficiaryId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Beneficiary not found"));
            if (!recipientBeneficiary.getVault().getId().equals(vaultId)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Beneficiary does not belong to this vault");
            }
        }

        String oldPathToDelete = null;

        if (attachment != null && !attachment.isEmpty()) {
            if (attachment.getSize() > MAX_ATTACHMENT_SIZE) {
                throw new BusinessException(ErrorCode.FILE_TOO_LARGE, "Attachment size exceeds 10MB limit");
            }

            long oldSize = message.getAttachmentSize() != null ? message.getAttachmentSize() : 0L;
            long delta = attachment.getSize() - oldSize;
            if (delta > 0) {
                entitlementService.assertStorageQuota(ownerId, delta);
            }

            oldPathToDelete = message.getAttachmentPath();

            byte[] cipherBytes = cryptoService.encryptBytes(attachment.getBytes());
            String newPath = fileStorageService.storeFile(attachment, cipherBytes);

            message.setAttachmentPath(newPath);
            message.setAttachmentName(attachment.getOriginalFilename());
            message.setAttachmentType(attachment.getContentType());
            message.setAttachmentSize(attachment.getSize());
        }

        message.setRecipientType(request.getRecipientType());
        message.setRecipientBeneficiary(recipientBeneficiary);
        message.setTitle(request.getTitle());
        message.setContent(cryptoService.encrypt(request.getContent()));
        message.setDeliveryTrigger(request.getDeliveryTrigger());
        message.setDeliverDate(request.getDeliveryTrigger() == LegacyMessageDeliveryTrigger.SPECIFIC_DATE ? request.getDeliverDate() : null);

        LegacyMessage saved = legacyMessageRepository.save(message);

        if (oldPathToDelete != null) {
            schedulePhysicalDelete(oldPathToDelete);
        }

        auditLogService.log(AuditAction.VAULT_UPDATED, AuditResult.SUCCESS, ownerId, null,
                vaultId, "Vault", vaultId.toString(), "Updated legacy message: " + saved.getTitle());

        return toResponse(saved, request.getContent(), true);
    }

    @Override
    @Transactional
    public void deleteMessage(UUID ownerId, UUID vaultId, UUID messageId) {
        Vault vault = getVaultForOwner(vaultId, ownerId);
        vaultModificationGuard.ensureModifiable(vault);

        LegacyMessage message = legacyMessageRepository.findByIdAndVaultId(messageId, vaultId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Legacy message not found"));

        String pathToDelete = message.getAttachmentPath();
        legacyMessageRepository.delete(message);

        if (pathToDelete != null) {
            schedulePhysicalDelete(pathToDelete);
        }

        auditLogService.log(AuditAction.VAULT_UPDATED, AuditResult.SUCCESS, ownerId, null,
                vaultId, "Vault", vaultId.toString(), "Deleted legacy message: " + message.getTitle());
    }

    @Override
    @Transactional(readOnly = true)
    public DecryptedAttachment getAttachment(UUID ownerId, UUID vaultId, UUID messageId) throws Exception {
        getVaultForOwner(vaultId, ownerId);
        LegacyMessage message = legacyMessageRepository.findByIdAndVaultId(messageId, vaultId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Legacy message not found"));

        if (message.getAttachmentPath() == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Message has no attachment");
        }

        byte[] cipherBytes = fileStorageService.readFile(message.getAttachmentPath());
        byte[] plainBytes = cryptoService.decryptBytes(cipherBytes);
        return new DecryptedAttachment(message.getAttachmentName(), message.getAttachmentType(), plainBytes);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LegacyMessageResponse> getMessagesForBeneficiary(UUID userId, UUID vaultId) {
        VaultBeneficiary beneficiary = requireActiveBeneficiary(vaultId, userId);

        Vault vault = beneficiary.getVault();
        if (!HANDOVER_VAULT_STATUSES.contains(vault.getStatus())) {
            throw new BusinessException(ErrorCode.CLAIM_NOT_READY, "Vault is not unlocked for beneficiaries yet");
        }

        List<LegacyMessage> messages = legacyMessageRepository.findForBeneficiary(
                vaultId, beneficiary.getId(), LegacyMessageRecipientType.ALL_BENEFICIARIES);

        // M2: Chỉ giải mã nội dung khi người thụ hưởng đã hoàn tất xác thực danh tính (session 30 phút hợp lệ)
        boolean hasViewSession = hasValidViewSession(vaultId, userId);
        LocalDate today = LocalDate.now(VN_ZONE);
        return messages.stream().map(m -> {
            boolean isUnlocked = isMessageUnlocked(m, today);
            String content = (isUnlocked && hasViewSession) ? decryptContentSafely(m.getContent()) : null;
            return toResponse(m, content, isUnlocked);
        }).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public LegacyMessageResponse getMessageDetailForBeneficiary(UUID userId, UUID vaultId, UUID messageId) throws Exception {
        VaultBeneficiary beneficiary = requireActiveBeneficiary(vaultId, userId);
        // M2: Yêu cầu phiên xác thực danh tính hợp lệ trước khi xem chi tiết lời nhắn
        assertViewSession(vaultId, userId);

        Vault vault = beneficiary.getVault();
        if (!HANDOVER_VAULT_STATUSES.contains(vault.getStatus())) {
            throw new BusinessException(ErrorCode.CLAIM_NOT_READY, "Vault is not unlocked for beneficiaries yet");
        }

        LegacyMessage message = legacyMessageRepository.findByIdAndVaultId(messageId, vaultId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Legacy message not found"));

        if (message.getRecipientType() == LegacyMessageRecipientType.SPECIFIC_BENEFICIARY
                && (message.getRecipientBeneficiary() == null || !message.getRecipientBeneficiary().getId().equals(beneficiary.getId()))) {
            // m2: không gửi cho mình -> NOT_FOUND để tránh IDOR probing
            throw new BusinessException(ErrorCode.NOT_FOUND, "Legacy message not found");
        }

        boolean isUnlocked = isMessageUnlocked(message, LocalDate.now(VN_ZONE));
        if (!isUnlocked) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "This time-capsule message is scheduled for " + message.getDeliverDate());
        }

        String decrypted = cryptoService.decrypt(message.getContent());
        return toResponse(message, decrypted, true);
    }

    @Override
    @Transactional(readOnly = true)
    public DecryptedAttachment getAttachmentForBeneficiary(UUID userId, UUID vaultId, UUID messageId) throws Exception {
        VaultBeneficiary beneficiary = requireActiveBeneficiary(vaultId, userId);
        // M2: Yêu cầu phiên xác thực danh tính hợp lệ trước khi tải tệp đính kèm
        assertViewSession(vaultId, userId);

        Vault vault = beneficiary.getVault();
        if (!HANDOVER_VAULT_STATUSES.contains(vault.getStatus())) {
            throw new BusinessException(ErrorCode.CLAIM_NOT_READY, "Vault is not unlocked for beneficiaries yet");
        }

        LegacyMessage message = legacyMessageRepository.findByIdAndVaultId(messageId, vaultId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Legacy message not found"));

        if (message.getRecipientType() == LegacyMessageRecipientType.SPECIFIC_BENEFICIARY
                && (message.getRecipientBeneficiary() == null || !message.getRecipientBeneficiary().getId().equals(beneficiary.getId()))) {
            // m2: NOT_FOUND
            throw new BusinessException(ErrorCode.NOT_FOUND, "Legacy message not found");
        }

        boolean isUnlocked = isMessageUnlocked(message, LocalDate.now(VN_ZONE));
        if (!isUnlocked) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "This time-capsule message is scheduled for " + message.getDeliverDate());
        }

        if (message.getAttachmentPath() == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Message has no attachment");
        }

        byte[] cipherBytes = fileStorageService.readFile(message.getAttachmentPath());
        byte[] plainBytes = cryptoService.decryptBytes(cipherBytes);
        return new DecryptedAttachment(message.getAttachmentName(), message.getAttachmentType(), plainBytes);
    }

    // F4b: Khi Vault được mở khóa (hoặc nhận bàn giao), phát tất cả lời nhắn ON_CLAIM và các lời nhắn SPECIFIC_DATE đã tới hạn
    @Override
    public int deliverOnClaimMessages(UUID vaultId) {
        Vault vault = vaultRepository.findById(vaultId).orElse(null);
        if (vault == null || !HANDOVER_VAULT_STATUSES.contains(vault.getStatus())) {
            return 0;
        }

        LocalDate today = LocalDate.now(VN_ZONE);
        LocalDateTime now = LocalDateTime.now();
        List<LegacyMessage> activeMessages = legacyMessageRepository.findByVaultIdAndStatus(vaultId, LegacyMessageStatus.ACTIVE);

        int deliveredCount = 0;
        for (LegacyMessage message : activeMessages) {
            if (isMessageUnlocked(message, today)) {
                if (deliverSingleMessageById(message.getId(), now)) {
                    deliveredCount++;
                }
            }
        }
        return deliveredCount;
    }

    // F4b: Scheduler hằng ngày quét và gửi các lời nhắn đến hạn (deliver_date <= hôm nay) hoặc ON_CLAIM chưa gửi trong các Vault đã mở
    @Override
    public int deliverDueScheduledMessages(LocalDate today) {
        LocalDate effectiveDate = today != null ? today : LocalDate.now(VN_ZONE);
        LocalDateTime now = LocalDateTime.now();

        List<LegacyMessage> dueMessages = legacyMessageRepository.findDueMessagesForUnlockedVaults(
                LegacyMessageStatus.ACTIVE,
                HANDOVER_VAULT_STATUSES,
                LegacyMessageDeliveryTrigger.ON_CLAIM,
                LegacyMessageDeliveryTrigger.SPECIFIC_DATE,
                effectiveDate);

        int deliveredCount = 0;
        for (LegacyMessage message : dueMessages) {
            if (deliverSingleMessageById(message.getId(), now)) {
                deliveredCount++;
            }
        }
        return deliveredCount;
    }

    private record RecipientInfo(UUID beneficiaryId, String email, String fullName, boolean hasAccount) {}
    private record DeliveryPlan(UUID messageId, UUID vaultId, String vaultName, String ownerName, List<RecipientInfo> recipients) {}

    private boolean deliverSingleMessageById(UUID messageId, LocalDateTime now) {
        DeliveryPlan plan;
        try {
            plan = requiresNewTx.execute(status -> {
                // F4b: Chuyển trạng thái ACTIVE -> DELIVERED nguyên tử để ngăn race condition giữa Listener và Scheduler
                int updated = legacyMessageRepository.markDelivered(messageId, now);
                if (updated == 0) {
                    return null;
                }

                // M1: Load lại entity trong chính session của transaction mới này để tránh LazyInitializationException
                LegacyMessage message = legacyMessageRepository.findById(messageId).orElse(null);
                if (message == null) {
                    return null;
                }
                Vault vault = message.getVault();
                if (vault == null || !HANDOVER_VAULT_STATUSES.contains(vault.getStatus())) {
                    return null;
                }
                message.setStatus(LegacyMessageStatus.DELIVERED);
                message.setDeliveredAt(now);

                List<VaultBeneficiary> beneficiaries = resolveRecipientsForDelivery(message, vault.getId());
                User owner = vault.getOwner();
                String ownerName = (owner != null && owner.getFullName() != null && !owner.getFullName().isBlank())
                        ? owner.getFullName()
                        : (owner != null && owner.getEmail() != null ? owner.getEmail() : "the vault owner");

                List<RecipientInfo> recipients = new ArrayList<>();
                for (VaultBeneficiary vb : beneficiaries) {
                    boolean hasAccount = vb.getUser() != null;
                    recipients.add(new RecipientInfo(vb.getId(), vb.getEmail(), vb.getFullName(), hasAccount));

                    if (hasAccount) {
                        try {
                            // n1: Nội dung thông báo không làm lộ tiêu đề riêng tư của lời nhắn
                            String content = String.format(
                                    "You have a new legacy message from %s in vault \"%s\".",
                                    ownerName, vault.getName());
                            notificationRepository.save(Notification.builder()
                                    .recipient(vb.getUser())
                                    .type(NotificationType.LEGACY_MESSAGE_DELIVERED)
                                    .channel(NotificationChannel.IN_APP)
                                    .referenceType("Vault")
                                    .referenceId(vault.getId())
                                    .content(content)
                                    .status(NotificationStatus.SENT)
                                    .sentAt(now)
                                    .build());
                        } catch (RuntimeException e) {
                            log.warn("F4b: failed to save in-app notification for message {} to beneficiary {}: {}",
                                    message.getId(), vb.getId(), e.getMessage());
                        }
                    }
                }

                // Ghi audit không chứa tiêu đề riêng tư của lời nhắn
                auditLogService.log(AuditAction.BENEFICIARY_NOTIFIED, AuditResult.SUCCESS, null, null,
                        vault.getId(), "LegacyMessage", message.getId().toString(),
                        "Delivered legacy message (id=" + message.getId() + ", recipients=" + recipients.size() + ")");

                return new DeliveryPlan(message.getId(), vault.getId(), vault.getName(), ownerName, recipients);
            });
        } catch (RuntimeException e) {
            log.error("F4b: failed to deliver message {} in new transaction: {}", messageId, e.getMessage(), e);
            return false;
        }

        if (plan == null) {
            return false;
        }

        // Giao dịch DB đã commit thành công: gửi mail sau commit
        for (RecipientInfo r : plan.recipients()) {
            if (r.email() != null && !r.email().isBlank()) {
                try {
                    emailSender.sendLegacyMessageDeliveredNotification(
                            r.email(), r.fullName(), plan.ownerName(), plan.vaultName(), plan.vaultId(), r.hasAccount());
                } catch (RuntimeException e) {
                    log.warn("F4b: failed to send legacy message email for message {} to {}: {}",
                            plan.messageId(), r.email(), e.getMessage());
                }
            }
        }
        return true;
    }

    private List<VaultBeneficiary> resolveRecipientsForDelivery(LegacyMessage message, UUID vaultId) {
        if (message.getRecipientType() == LegacyMessageRecipientType.SPECIFIC_BENEFICIARY) {
            VaultBeneficiary target = message.getRecipientBeneficiary();
            if (target != null && isEligibleRecipient(target)) {
                return List.of(target);
            }
            return List.of();
        }
        return vaultBeneficiaryRepository.findByVaultIdOrderByCreatedAtAsc(vaultId).stream()
                .filter(this::isEligibleRecipient)
                .toList();
    }

    private boolean isEligibleRecipient(VaultBeneficiary vb) {
        return vb.getStatus() != BeneficiaryInviteStatus.DECLINED
                && vb.getStatus() != BeneficiaryInviteStatus.WAIVED;
    }

    private VaultBeneficiary requireActiveBeneficiary(UUID vaultId, UUID userId) {
        VaultBeneficiary beneficiary = vaultBeneficiaryRepository.findByVaultIdAndUserId(vaultId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Vault not found"));
        if (!isEligibleRecipient(beneficiary)) {
            throw new BusinessException(ErrorCode.CLAIM_ALREADY_RESPONDED, "You have declined this inheritance");
        }
        return beneficiary;
    }

    private boolean hasValidViewSession(UUID vaultId, UUID currentUserId) {
        return identityVerificationRepository.existsByVaultIdAndBeneficiaryIdAndStatusAndVerifiedAtAfter(
                vaultId, currentUserId, VerificationStatus.SUCCESS,
                LocalDateTime.now().minusMinutes(VIEW_SESSION_MINUTES));
    }

    private void assertViewSession(UUID vaultId, UUID currentUserId) {
        if (!hasValidViewSession(vaultId, currentUserId)) {
            throw new BusinessException(ErrorCode.IDENTITY_NOT_VERIFIED,
                    "Identity verification is required before viewing messages.");
        }
    }

    private boolean isMessageUnlocked(LegacyMessage m, LocalDate today) {
        if (m.getStatus() == LegacyMessageStatus.DELIVERED) {
            return true;
        }
        if (m.getDeliveryTrigger() == LegacyMessageDeliveryTrigger.ON_CLAIM) {
            return true;
        }
        return m.getDeliverDate() != null && !m.getDeliverDate().isAfter(today);
    }

    private String decryptContentSafely(String cipher) {
        try {
            return cryptoService.decrypt(cipher);
        } catch (Exception e) {
            log.warn("F4b: failed to decrypt message content: {}", e.getMessage());
            return "[Encrypted message content unavailable]";
        }
    }

    private Vault getVaultForOwner(UUID vaultId, UUID ownerId) {
        Vault vault = vaultRepository.findById(vaultId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Vault not found"));
        if (!vault.getOwner().getId().equals(ownerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "You do not own this vault");
        }
        return vault;
    }

    private void validateDeliveryTrigger(LegacyMessageDeliveryTrigger trigger, LocalDate deliverDate) {
        if (trigger == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "deliveryTrigger is required");
        }
        if (trigger == LegacyMessageDeliveryTrigger.SPECIFIC_DATE) {
            if (deliverDate == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "deliverDate is required when deliveryTrigger is SPECIFIC_DATE");
            }
            if (deliverDate.isBefore(LocalDate.now(VN_ZONE))) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "deliverDate cannot be in the past");
            }
        }
    }

    private void schedulePhysicalDelete(String path) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    fileStorageService.deleteFile(path);
                }
            });
        } else {
            fileStorageService.deleteFile(path);
        }
    }

    private LegacyMessageResponse toResponse(LegacyMessage m, String decryptedContent, boolean isUnlocked) {
        String recipientName = null;
        if (m.getRecipientType() == LegacyMessageRecipientType.SPECIFIC_BENEFICIARY && m.getRecipientBeneficiary() != null) {
            recipientName = m.getRecipientBeneficiary().getFullName() != null
                    ? m.getRecipientBeneficiary().getFullName()
                    : m.getRecipientBeneficiary().getEmail();
        }

        return LegacyMessageResponse.builder()
                .id(m.getId())
                .vaultId(m.getVault().getId())
                .recipientType(m.getRecipientType())
                .recipientBeneficiaryId(m.getRecipientBeneficiary() != null ? m.getRecipientBeneficiary().getId() : null)
                .recipientName(recipientName)
                .title(m.getTitle())
                .content(decryptedContent)
                .deliveryTrigger(m.getDeliveryTrigger())
                .deliverDate(m.getDeliverDate())
                .status(m.getStatus())
                .unlocked(isUnlocked)
                .deliveredAt(m.getDeliveredAt())
                .hasAttachment(m.getAttachmentPath() != null)
                .attachmentName(m.getAttachmentName())
                .attachmentType(m.getAttachmentType())
                .attachmentSize(m.getAttachmentSize())
                .createdAt(m.getCreatedAt())
                .updatedAt(m.getUpdatedAt())
                .build();
    }
}
