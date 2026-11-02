package com.ltld.app.legacyvault.service.messageservice;

import com.ltld.app.legacyvault.dto.messagedto.CreateLegacyMessageRequest;
import com.ltld.app.legacyvault.dto.messagedto.LegacyMessageResponse;
import com.ltld.app.legacyvault.dto.messagedto.UpdateLegacyMessageRequest;
import com.ltld.app.legacyvault.entity.LegacyMessage;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.entity.VaultBeneficiary;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.LegacyMessageDeliveryTrigger;
import com.ltld.app.legacyvault.enums.LegacyMessageRecipientType;
import com.ltld.app.legacyvault.enums.LegacyMessageStatus;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.repository.LegacyMessageRepository;
import com.ltld.app.legacyvault.repository.VaultBeneficiaryRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import com.ltld.app.legacyvault.service.fileservice.FileStorageService;
import com.ltld.app.legacyvault.service.paymentservice.EntitlementService;
import com.ltld.app.legacyvault.service.vaultservice.VaultModificationGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LegacyMessageServiceImpl implements LegacyMessageService {

    private static final long MAX_ATTACHMENT_SIZE = 25 * 1024 * 1024L; // 25MB

    private final LegacyMessageRepository legacyMessageRepository;
    private final VaultRepository vaultRepository;
    private final VaultBeneficiaryRepository vaultBeneficiaryRepository;
    private final CryptoService cryptoService;
    private final FileStorageService fileStorageService;
    private final EntitlementService entitlementService;
    private final VaultModificationGuard vaultModificationGuard;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public LegacyMessageResponse createMessage(UUID ownerId, UUID vaultId, CreateLegacyMessageRequest request, MultipartFile attachment) throws Exception {
        Vault vault = getVaultForOwner(vaultId, ownerId);
        vaultModificationGuard.ensureModifiable(vault);
        entitlementService.assertCanUseLegacyMessages(ownerId);

        VaultBeneficiary recipient = validateAndResolveRecipient(vault, request.getRecipientType(), request.getRecipientBeneficiaryId());
        validateDeliveryTrigger(request.getDeliveryTrigger(), request.getDeliverDate());

        String encryptedContent = cryptoService.encrypt(request.getContent());

        LegacyMessage message = LegacyMessage.builder()
                .vault(vault)
                .recipientType(request.getRecipientType())
                .recipientBeneficiary(recipient)
                .title(request.getTitle().trim())
                .content(encryptedContent)
                .deliveryTrigger(request.getDeliveryTrigger())
                .deliverDate(request.getDeliveryTrigger() == LegacyMessageDeliveryTrigger.SPECIFIC_DATE ? request.getDeliverDate() : null)
                .status(LegacyMessageStatus.ACTIVE)
                .build();

        if (attachment != null && !attachment.isEmpty()) {
            validateAttachment(attachment);
            byte[] encryptedBytes = cryptoService.encryptBytes(attachment.getBytes());
            String savedPath = fileStorageService.storeFile(attachment, encryptedBytes);
            message.setAttachmentName(attachment.getOriginalFilename());
            message.setAttachmentPath(savedPath);
            message.setAttachmentType(attachment.getContentType());
            message.setAttachmentSize(attachment.getSize());
        }

        LegacyMessage saved = legacyMessageRepository.save(message);

        auditLogService.log(AuditAction.VAULT_UPDATED, AuditResult.SUCCESS, ownerId, null,
                vaultId, "Vault", vaultId.toString(), "Created legacy message: " + saved.getTitle());

        return toResponse(saved, request.getContent(), true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LegacyMessageResponse> getMessagesByVault(UUID ownerId, UUID vaultId) {
        Vault vault = getVaultForOwner(vaultId, ownerId);
        return legacyMessageRepository.findByVaultIdOrderByCreatedAtDesc(vault.getId()).stream()
                .map(m -> {
                    String decrypted = decryptContentSafely(m.getContent());
                    return toResponse(m, decrypted, true);
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public LegacyMessageResponse getMessageById(UUID ownerId, UUID vaultId, UUID messageId) throws Exception {
        getVaultForOwner(vaultId, ownerId);
        LegacyMessage message = legacyMessageRepository.findByIdAndVaultId(messageId, vaultId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Legacy message not found"));
        String decrypted = cryptoService.decrypt(message.getContent());
        return toResponse(message, decrypted, true);
    }

    @Override
    @Transactional
    public LegacyMessageResponse updateMessage(UUID ownerId, UUID vaultId, UUID messageId, UpdateLegacyMessageRequest request, MultipartFile attachment) throws Exception {
        Vault vault = getVaultForOwner(vaultId, ownerId);
        vaultModificationGuard.ensureModifiable(vault);

        LegacyMessage message = legacyMessageRepository.findByIdAndVaultId(messageId, vaultId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Legacy message not found"));

        VaultBeneficiary recipient = validateAndResolveRecipient(vault, request.getRecipientType(), request.getRecipientBeneficiaryId());
        validateDeliveryTrigger(request.getDeliveryTrigger(), request.getDeliverDate());

        message.setTitle(request.getTitle().trim());
        message.setContent(cryptoService.encrypt(request.getContent()));
        message.setRecipientType(request.getRecipientType());
        message.setRecipientBeneficiary(recipient);
        message.setDeliveryTrigger(request.getDeliveryTrigger());
        message.setDeliverDate(request.getDeliveryTrigger() == LegacyMessageDeliveryTrigger.SPECIFIC_DATE ? request.getDeliverDate() : null);

        String oldPathToDelete = null;

        if (attachment != null && !attachment.isEmpty()) {
            validateAttachment(attachment);
            if (message.getAttachmentPath() != null) {
                oldPathToDelete = message.getAttachmentPath();
            }
            byte[] encryptedBytes = cryptoService.encryptBytes(attachment.getBytes());
            String savedPath = fileStorageService.storeFile(attachment, encryptedBytes);
            message.setAttachmentName(attachment.getOriginalFilename());
            message.setAttachmentPath(savedPath);
            message.setAttachmentType(attachment.getContentType());
            message.setAttachmentSize(attachment.getSize());
        } else if (!request.isKeepExistingAttachment() && message.getAttachmentPath() != null) {
            oldPathToDelete = message.getAttachmentPath();
            message.setAttachmentName(null);
            message.setAttachmentPath(null);
            message.setAttachmentType(null);
            message.setAttachmentSize(null);
        }

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
        VaultBeneficiary beneficiary = vaultBeneficiaryRepository.findByVaultIdAndUserId(vaultId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "You are not a beneficiary of this vault"));

        Vault vault = beneficiary.getVault();
        if (vault.getStatus() != VaultStatus.UNLOCKED && vault.getStatus() != VaultStatus.CLAIMED) {
            throw new BusinessException(ErrorCode.CLAIM_NOT_READY, "Vault is not unlocked for beneficiaries yet");
        }

        List<LegacyMessage> messages = legacyMessageRepository.findForBeneficiary(
                vaultId, beneficiary.getId(), LegacyMessageRecipientType.ALL_BENEFICIARIES);

        LocalDate today = LocalDate.now();
        return messages.stream().map(m -> {
            boolean isUnlocked = isMessageUnlocked(m, today);
            String content = isUnlocked ? decryptContentSafely(m.getContent()) : null;
            return toResponse(m, content, isUnlocked);
        }).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public LegacyMessageResponse getMessageDetailForBeneficiary(UUID userId, UUID vaultId, UUID messageId) throws Exception {
        VaultBeneficiary beneficiary = vaultBeneficiaryRepository.findByVaultIdAndUserId(vaultId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "You are not a beneficiary of this vault"));

        Vault vault = beneficiary.getVault();
        if (vault.getStatus() != VaultStatus.UNLOCKED && vault.getStatus() != VaultStatus.CLAIMED) {
            throw new BusinessException(ErrorCode.CLAIM_NOT_READY, "Vault is not unlocked for beneficiaries yet");
        }

        LegacyMessage message = legacyMessageRepository.findByIdAndVaultId(messageId, vaultId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Legacy message not found"));

        if (message.getRecipientType() == LegacyMessageRecipientType.SPECIFIC_BENEFICIARY
                && (message.getRecipientBeneficiary() == null || !message.getRecipientBeneficiary().getId().equals(beneficiary.getId()))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "This message is not addressed to you");
        }

        boolean isUnlocked = isMessageUnlocked(message, LocalDate.now());
        if (!isUnlocked) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "This time-capsule message is scheduled for " + message.getDeliverDate());
        }

        String decrypted = cryptoService.decrypt(message.getContent());
        return toResponse(message, decrypted, true);
    }

    @Override
    @Transactional(readOnly = true)
    public DecryptedAttachment getAttachmentForBeneficiary(UUID userId, UUID vaultId, UUID messageId) throws Exception {
        VaultBeneficiary beneficiary = vaultBeneficiaryRepository.findByVaultIdAndUserId(vaultId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, "You are not a beneficiary of this vault"));

        Vault vault = beneficiary.getVault();
        if (vault.getStatus() != VaultStatus.UNLOCKED && vault.getStatus() != VaultStatus.CLAIMED) {
            throw new BusinessException(ErrorCode.CLAIM_NOT_READY, "Vault is not unlocked for beneficiaries yet");
        }

        LegacyMessage message = legacyMessageRepository.findByIdAndVaultId(messageId, vaultId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Legacy message not found"));

        if (message.getRecipientType() == LegacyMessageRecipientType.SPECIFIC_BENEFICIARY
                && (message.getRecipientBeneficiary() == null || !message.getRecipientBeneficiary().getId().equals(beneficiary.getId()))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "This message is not addressed to you");
        }

        boolean isUnlocked = isMessageUnlocked(message, LocalDate.now());
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

    private boolean isMessageUnlocked(LegacyMessage m, LocalDate today) {
        if (m.getStatus() == LegacyMessageStatus.DELIVERED) {
            return true;
        }
        if (m.getDeliveryTrigger() == LegacyMessageDeliveryTrigger.ON_CLAIM) {
            return true;
        }
        return m.getDeliverDate() != null && !m.getDeliverDate().isAfter(today);
    }

    private Vault getVaultForOwner(UUID vaultId, UUID ownerId) {
        Vault vault = vaultRepository.findById(vaultId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Vault not found"));
        if (!vault.getOwner().getId().equals(ownerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "You do not own this vault");
        }
        return vault;
    }

    private VaultBeneficiary validateAndResolveRecipient(Vault vault, LegacyMessageRecipientType recipientType, UUID recipientBeneficiaryId) {
        if (recipientType == LegacyMessageRecipientType.SPECIFIC_BENEFICIARY) {
            if (recipientBeneficiaryId == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "recipientBeneficiaryId is required when recipientType is SPECIFIC_BENEFICIARY");
            }
            VaultBeneficiary beneficiary = vaultBeneficiaryRepository.findById(recipientBeneficiaryId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Recipient beneficiary not found"));
            if (!beneficiary.getVault().getId().equals(vault.getId())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Beneficiary does not belong to this vault");
            }
            return beneficiary;
        }
        return null;
    }

    private void validateDeliveryTrigger(LegacyMessageDeliveryTrigger trigger, LocalDate deliverDate) {
        if (trigger == LegacyMessageDeliveryTrigger.SPECIFIC_DATE) {
            if (deliverDate == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "deliverDate is required when deliveryTrigger is SPECIFIC_DATE");
            }
            if (deliverDate.isBefore(LocalDate.now())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "deliverDate cannot be in the past");
            }
        }
    }

    private void validateAttachment(MultipartFile file) {
        if (file.getSize() > MAX_ATTACHMENT_SIZE) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE, "Attachment size exceeds 25MB limit");
        }
        String filename = file.getOriginalFilename();
        if (filename != null) {
            String lower = filename.toLowerCase();
            if (lower.endsWith(".exe") || lower.endsWith(".bat") || lower.endsWith(".sh") || lower.endsWith(".cmd")) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Executable files are not allowed");
            }
        }
    }

    private String decryptContentSafely(String encrypted) {
        try {
            return cryptoService.decrypt(encrypted);
        } catch (Exception e) {
            log.warn("Failed to decrypt legacy message content: {}", e.getMessage());
            return "[Encrypted content]";
        }
    }

    private void schedulePhysicalDelete(String path) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        fileStorageService.deleteFile(path);
                    } catch (Exception ex) {
                        log.warn("Failed to physically delete file after commit: {}", path, ex);
                    }
                }
            });
        } else {
            try {
                fileStorageService.deleteFile(path);
            } catch (Exception ex) {
                log.warn("Failed to physically delete file: {}", path, ex);
            }
        }
    }

    private LegacyMessageResponse toResponse(LegacyMessage m, String content, boolean unlocked) {
        String recipientName = null;
        String recipientEmail = null;
        UUID recipientId = null;

        if (m.getRecipientBeneficiary() != null) {
            recipientId = m.getRecipientBeneficiary().getId();
            recipientName = m.getRecipientBeneficiary().getFullName();
            recipientEmail = m.getRecipientBeneficiary().getEmail();
        }

        return LegacyMessageResponse.builder()
                .id(m.getId())
                .vaultId(m.getVault().getId())
                .recipientType(m.getRecipientType())
                .recipientBeneficiaryId(recipientId)
                .recipientName(recipientName)
                .recipientEmail(recipientEmail)
                .title(m.getTitle())
                .content(content)
                .deliveryTrigger(m.getDeliveryTrigger())
                .deliverDate(m.getDeliverDate())
                .status(m.getStatus())
                .hasAttachment(m.getAttachmentPath() != null)
                .attachmentName(m.getAttachmentName())
                .attachmentType(m.getAttachmentType())
                .attachmentSize(m.getAttachmentSize())
                .createdAt(m.getCreatedAt())
                .updatedAt(m.getUpdatedAt())
                .deliveredAt(m.getDeliveredAt())
                .unlocked(unlocked)
                .build();
    }
}
