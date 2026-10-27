package com.ltld.app.legacyvault.service.beneficiaryservice;

import com.ltld.app.legacyvault.entity.BeneficiaryClaim;
import com.ltld.app.legacyvault.entity.Notification;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.ClaimStatus;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.NotificationChannel;
import com.ltld.app.legacyvault.enums.NotificationStatus;
import com.ltld.app.legacyvault.enums.NotificationType;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.event.VaultsUnlockedEvent;
import com.ltld.app.legacyvault.repository.AuditLogRepository;
import com.ltld.app.legacyvault.repository.BeneficiaryClaimRepository;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import com.ltld.app.legacyvault.repository.NotificationRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.notificationservice.ExecutorNotificationService;
import com.ltld.app.legacyvault.utility.EmailSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * FR-16: khi Legal Verifier ký mở khóa (FR-14 phát VaultsUnlockedEvent), thông báo cho Beneficiary
 * của từng Vault. Mỗi Vault đi qua 3 pha:
 *  1. Chuẩn bị (giao dịch riêng): kiểm tra, tạo BeneficiaryClaim PENDING nếu chưa có, đếm lượt thử, commit.
 *  1b. Thông báo in-app (giao dịch riêng, có try/catch): lỗi ở đây không được chặn email.
 *  2. Gửi mail (ngoài giao dịch, đồng bộ): lỗi thì không ghi notifiedAt, ghi audit FAILED.
 *  3. Ghi nhận (giao dịch riêng): chỉ khi gửi thành công mới set notifiedAt và ghi audit SUCCESS.
 * Vault chưa gửi được sẽ được BeneficiaryNotificationRetryScheduler gửi lại theo lịch giãn dần
 * (retryDelay), tối đa MAX_NOTIFY_ATTEMPTS lần. Hết lượt thì ghi audit GAVE_UP và báo Executor ACTIVE
 * để liên hệ Beneficiary ngoài hệ thống.
 *  * Q25: nếu Beneficiary chưa có tài khoản đã xác minh (chỉ có beneficiaryEmail) thì gửi mail mời đăng ký
 *  * và báo Executor ACTIVE, không tạo claim; khi họ xác minh email, scheduler gửi thông báo bàn giao thường.
 */
@Slf4j
@Component
public class BeneficiaryNotificationListener {

    // 12 lần: sau lần thử thứ n thất bại thì chờ retryDelay(n) = 1h, 2h, 4h, 8h, 16h rồi 24h mỗi lần
    // -> lần cuối cách lần đầu khoảng 7,3 ngày, đủ sống sót qua sự cố SMTP kéo dài nhiều ngày.
    static final int MAX_NOTIFY_ATTEMPTS = 12;
    // Giới hạn số Vault mỗi lượt quét để không giữ thread scheduler (dùng chung với FR-07) quá lâu
    static final int RETRY_BATCH_SIZE = 50;
    // Vault vừa mở khóa còn thuộc về listener sự kiện; scheduler bỏ qua để không gửi trùng
    static final Duration RETRY_GRACE = Duration.ofMinutes(5);

    private static final String ENTITY_VAULT = "Vault";
    private static final String OWNER_FALLBACK = "the owner";
    // Tham số: tên Vault, tên Owner, hạn nhận
    private static final String MSG_IN_APP = "The vault \"%s\" of %s has been handed over to you. Claim deadline: %s (GMT+7).";

    // Tham số: tên Vault, tên Owner, email Beneficiary, số lần đã thử, hạn nhận
    private static final String MSG_GAVE_UP =
            "The system could not deliver the handover notice for vault \"%s\" of %s to the beneficiary (%s) after %d attempts.%n"
                    + "Please contact the beneficiary and guide them to sign in to LegacyVault to claim the assets "
                    + "before %s (GMT+7).";
    // Tham số: tên Vault, tên Owner, hạn nhận
    private static final String MSG_PENDING_REGISTRATION =
            "The vault \"%s\" of %s has been unlocked, but the beneficiary does not have a verified account yet.%n"
                    + "Please contact them so they can register and verify their email before the claim deadline %s (GMT+7).";

    private final VaultRepository vaultRepository;
    private final BeneficiaryClaimRepository claimRepository;
    private final ExecutorAssignmentRepository executorAssignmentRepository;
    private final EmailSender emailSender;
    private final ExecutorNotificationService executorNotificationService;
    private final AuditLogService auditLogService;
    private final NotificationRepository notificationRepository;
    private final AuditLogRepository auditLogRepository;
    private final TransactionTemplate requiresNewTx;

    public BeneficiaryNotificationListener(VaultRepository vaultRepository,
                                           BeneficiaryClaimRepository claimRepository,
                                           ExecutorAssignmentRepository executorAssignmentRepository,
                                           EmailSender emailSender,
                                           ExecutorNotificationService executorNotificationService,
                                           AuditLogService auditLogService,
                                           NotificationRepository notificationRepository,
                                           AuditLogRepository auditLogRepository,
                                           PlatformTransactionManager transactionManager) {
        this.vaultRepository = vaultRepository;
        this.claimRepository = claimRepository;
        this.executorAssignmentRepository = executorAssignmentRepository;
        this.emailSender = emailSender;
        this.executorNotificationService = executorNotificationService;
        this.auditLogService = auditLogService;
        this.notificationRepository = notificationRepository;
        this.auditLogRepository = auditLogRepository;
        this.requiresNewTx = new TransactionTemplate(transactionManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Khoảng chờ sau lần thử thứ {@code attempts} thất bại: 1h, 2h, 4h, 8h, 16h, rồi 24h. */
    static Duration retryDelay(int attempts) {
        return Duration.ofHours(Math.min(24, 1L << Math.max(0, attempts - 1)));
    }

    // @Async trên listener (không phải trên hàm gửi mail): request FR-14 không phải chờ SMTP,
    // nhưng lỗi gửi vẫn được bắt và ghi nhận ngay trong luồng này.
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onVaultsUnlocked(VaultsUnlockedEvent event) {
        LocalDateTime now = LocalDateTime.now();
        event.vaultIds().forEach(vaultId -> processVault(vaultId, now));
    }

    /** Lượt quét gửi lại: Vault UNLOCKED còn hạn mà chưa gửi được thông báo. */
    public void retryPendingNotifications(LocalDateTime now) {
        List<UUID> vaultIds = vaultRepository.findVaultIdsToNotify(
                now, now.minus(RETRY_GRACE), now.minus(retryDelay(1)), MAX_NOTIFY_ATTEMPTS,
                PageRequest.of(0, RETRY_BATCH_SIZE));
        vaultIds.forEach(vaultId -> processVault(vaultId, now));
    }

    private void processVault(UUID vaultId, LocalDateTime now) {
        final MailData mail = tryPrepare(vaultId, now);
        if (mail == null) {
            return;
        }
        if (mail.pendingRegistration()) {
            notifyPendingRegistration(mail);
            return;
        }

        // Pha 1b: thông báo trong hệ thống, giao dịch riêng; lỗi ở đây không được chặn email
        try {
            requiresNewTx.executeWithoutResult(status -> createInAppNotification(mail));
        } catch (RuntimeException e) {
            log.warn("FR-16: vault {} - in-app notification not saved: {}", vaultId, e.getClass().getSimpleName(), e);
        }

        // Pha 2: gửi mail ngoài mọi giao dịch
        try {
            emailSender.sendVaultUnlockedNotification(mail.email(), mail.fullName(), mail.ownerName(),
                    mail.vaultName(), mail.claimDeadlineAt(), mail.accountActive(), mail.vaultId());
        } catch (RuntimeException e) {
            recordFailure(vaultId, "recipient=" + mail.email() + ", send failed (attempt "
                    + mail.attempt() + "/" + MAX_NOTIFY_ATTEMPTS + "): " + e.getClass().getSimpleName(), e);
            if (mail.attempt() >= MAX_NOTIFY_ATTEMPTS) {
                giveUp(mail);
            }
            return;
        }

        // Pha 3: gửi thành công mới ghi notifiedAt
        try {
            requiresNewTx.executeWithoutResult(status -> markNotified(mail.claimId));
        } catch (RuntimeException e) {
            recordFailure(vaultId, "recipient=" + mail.email()
                    + ", mail sent but notifiedAt not saved: " + e.getClass().getSimpleName(), e);
            return;
        }

        auditLogService.log(AuditAction.BENEFICIARY_NOTIFIED, AuditResult.SUCCESS, null, null,
                vaultId, ENTITY_VAULT, vaultId.toString(),
                "recipient=" + mail.email() + ", attempt=" + mail.attempt()
                        + ", accountStatus=" + mail.accountStatus());
    }

    // Lần thử cuối cũng lỗi: không còn lượt tự động nữa, nên ghi audit và báo Executor ACTIVE của Owner
    // (người đang xử lý hồ sơ pháp lý) để họ liên hệ Beneficiary ngoài hệ thống trước khi hết hạn nhận.
    private void giveUp(MailData mail) {
        String content = String.format(MSG_GAVE_UP, mail.vaultName(), mail.ownerName(), mail.email(),
                MAX_NOTIFY_ATTEMPTS, EmailSender.formatDeadline(mail.claimDeadlineAt()));
        int notified = 0;
        for (String executorEmail : mail.executorEmails()) {
            try {
                executorNotificationService.send(executorEmail, content);
                notified++;
            } catch (RuntimeException e) {
                log.warn("FR-16: could not tell executor about vault {}: {}", mail.vaultId(), e.getMessage());
            }
        }
        log.error("FR-16: vault {} gave up notifying beneficiary after {} attempts", mail.vaultId(), MAX_NOTIFY_ATTEMPTS);
        auditLogService.log(AuditAction.BENEFICIARY_NOTIFY_GAVE_UP, AuditResult.FAILURE, null, null,
                mail.vaultId(), ENTITY_VAULT, mail.vaultId().toString(),
                "recipient=" + mail.email() + ", attempts=" + MAX_NOTIFY_ATTEMPTS
                        + ", executorsNotified=" + notified + "/" + mail.executorEmails().size());
    }

    // Q25: Beneficiary chưa có tài khoản đã xác minh nên không có claim để theo dõi và không có retry.
    // Mail mời lỗi vẫn phải báo Executor (đường dự phòng). Audit không ghi email bên thứ ba.
    private void notifyPendingRegistration(MailData mail) {
        UUID vaultId = mail.vaultId();
        boolean mailSent = true;
        try {
            emailSender.sendVaultUnlockedNotification(mail.email(), null, mail.ownerName(),
                    mail.vaultName(), mail.claimDeadlineAt(), false, vaultId);
        } catch (RuntimeException e) {
            mailSent = false;
            recordFailure(vaultId, "pendingRegistration=true, invite send failed: "
                    + e.getClass().getSimpleName(), e);
        }

        String content = String.format(MSG_PENDING_REGISTRATION, mail.vaultName(), mail.ownerName(),
                EmailSender.formatDeadline(mail.claimDeadlineAt()));
        int notified = notifyExecutors(vaultId, mail.executorEmails(), content);

        if (mailSent) {
            auditLogService.log(AuditAction.BENEFICIARY_PENDING_NOTIFIED, AuditResult.SUCCESS, null, null,
                    vaultId, ENTITY_VAULT, vaultId.toString(),
                    "pendingRegistration=true, executorsNotified=" + notified + "/" + mail.executorEmails().size());
        }
    }

    private int notifyExecutors(UUID vaultId, List<String> executorEmails, String content) {
        int notified = 0;
        for (String executorEmail : executorEmails) {
            try {
                executorNotificationService.send(executorEmail, content);
                notified++;
            } catch (RuntimeException e) {
                log.warn("FR-16: could not tell executor about vault {}: {}", vaultId, e.getMessage());
            }
        }
        return notified;
    }

    // Pha 1 cho nhánh chờ đăng ký: không tạo claim, chỉ chặn phát lại sự kiện
    private MailData preparePendingRegistration(Vault vault) {
        UUID vaultId = vault.getId();
        if (auditLogRepository.existsByVaultIdAndAction(vaultId, AuditAction.BENEFICIARY_PENDING_NOTIFIED)) {
            log.info("FR-16: Vault {} already notified (pending registration), skip", vaultId);
            return null;
        }
        User owner = vault.getOwner();
        String ownerName = owner != null && owner.getFullName() != null && !owner.getFullName().isBlank()
                ? owner.getFullName() : OWNER_FALLBACK;
        return new MailData(null, vaultId, vault.getBeneficiaryEmail(), null, ownerName, vault.getName(),
                vault.getClaimDeadlineAt(), false, null, 0, activeExecutorEmails(owner), null, true);
    }

    private MailData tryPrepare(UUID vaultId, LocalDateTime now) {
        try {
            return requiresNewTx.execute(status -> prepare(vaultId, now));
        } catch (RuntimeException e) {
            recordFailure(vaultId, "prepare failed: " + e.getClass().getSimpleName(), e);
            return null;
        }
    }

    // Pha 1: trả về null nếu không cần/không thể gửi
    private MailData prepare(UUID vaultId, LocalDateTime now) {
        Vault vault = vaultRepository.findById(vaultId).orElse(null);
        if (vault == null || vault.getStatus() != VaultStatus.UNLOCKED) {
            log.warn("FR-16: Skip vault {} (not found or not UNLOCKED)", vaultId);
            return null;
        }

        // SRS FR-16 precondition: Vault phải có Beneficiary được gán, hoặc email đang chờ đăng ký (Q25)
        User beneficiary = vault.getBeneficiary();
        boolean pendingRegistration = beneficiary == null
                && vault.getBeneficiaryEmail() != null && !vault.getBeneficiaryEmail().isBlank();
        if (beneficiary == null && !pendingRegistration) {
            auditFailure(vaultId, "no beneficiary assigned");
            return null;
        }
        // FR-14 luôn set claimDeadlineAt khi mở khóa; thiếu nghĩa là dữ liệu lỗi, không tự đoán hạn
        if (vault.getClaimDeadlineAt() == null) {
            auditFailure(vaultId, "vault has no claimDeadlineAt");
            return null;
        }
        if (pendingRegistration) {
            return preparePendingRegistration(vault);
        }

        // Tạo claim PENDING nếu chưa có; initializeClaim sẽ dùng lại claim này
        BeneficiaryClaim claim = claimRepository.findByVaultId(vaultId)
                .orElseGet(() -> claimRepository.save(BeneficiaryClaim.builder()
                        .vault(vault)
                        .beneficiary(beneficiary)
                        .status(ClaimStatus.PENDING)
                        .claimDeadlineAt(vault.getClaimDeadlineAt())
                        .build()));

        // Đã gửi rồi (hoặc claim không còn PENDING): không gửi lại
        if (claim.getStatus() != ClaimStatus.PENDING || claim.getNotifiedAt() != null) {
            log.info("FR-16: Vault {} already notified or claim not pending, skip", vaultId);
            return null;
        }
        // Hết lượt: đã audit GAVE_UP ở lần thử cuối, không lặp lại
        if (claim.getNotifyAttempts() >= MAX_NOTIFY_ATTEMPTS) {
            log.warn("FR-16: Vault {} reached {} notify attempts, skip", vaultId, MAX_NOTIFY_ATTEMPTS);
            return null;
        }
        // Chưa tới lượt thử lại (giãn cách tăng dần); cũng chặn gửi trùng khi sự kiện bị phát lại
        if (claim.getLastNotifyAttemptAt() != null
                && claim.getLastNotifyAttemptAt().plus(retryDelay(claim.getNotifyAttempts())).isAfter(now)) {
            log.info("FR-16: Vault {} not due for another attempt yet, skip", vaultId);
            return null;
        }

        // Đếm lượt thử ngay ở pha này: nếu tiến trình chết giữa chừng thì lượt đó vẫn được tính
        claim.setNotifyAttempts(claim.getNotifyAttempts() + 1);
        claim.setLastNotifyAttemptAt(now);
        claimRepository.save(claim);

        User owner = vault.getOwner();
        String ownerName = owner != null && owner.getFullName() != null && !owner.getFullName().isBlank()
                ? owner.getFullName() : OWNER_FALLBACK;

        // Chỉ cần danh sách Executor ở lần thử cuối (để báo khi bỏ cuộc)
        List<String> executorEmails = claim.getNotifyAttempts() >= MAX_NOTIFY_ATTEMPTS
                ? activeExecutorEmails(owner) : List.of();

        return new MailData(claim.getId(), vaultId, beneficiary.getEmail(), beneficiary.getFullName(),
                ownerName, vault.getName(), claim.getClaimDeadlineAt(),
                beneficiary.getStatus() != UserStatus.PENDING,   // nhánh 2a: PENDING = chưa kích hoạt
                beneficiary.getStatus(), claim.getNotifyAttempts(), executorEmails,
                beneficiary, false);
    }

    private List<String> activeExecutorEmails(User owner) {
        if (owner == null) {
            return List.of();
        }
        return executorAssignmentRepository.findAllByOwnerId(owner.getId()).stream()
                .filter(a -> a.getStatus() == ExecutorStatus.ACTIVE)
                .map(a -> a.getExecutorUser().getEmail())
                .distinct()
                .toList();
    }

    // Pha 3: set notifiedAt (email đã gửi)
    private void markNotified(UUID claimId) {
        claimRepository.findById(claimId).ifPresent(claim -> {
            claim.setNotifiedAt(LocalDateTime.now());
            claimRepository.save(claim);
        });
    }

    // Idempotent: đã có thông báo VAULT_UNLOCKED cho Vault này thì bỏ qua; lượt thử lại tự bù nếu lượt trước lỗi
    private void createInAppNotification(MailData mail) {
        boolean exists = notificationRepository
                .findFirstByRecipientIdAndTypeAndReferenceTypeAndReferenceIdOrderByCreatedAtDesc(
                        mail.beneficiary().getId(), NotificationType.VAULT_UNLOCKED, ENTITY_VAULT, mail.vaultId())
                .isPresent();
        if (exists) {
            return;
        }
        notificationRepository.save(Notification.builder()
                .recipient(mail.beneficiary())
                .type(NotificationType.VAULT_UNLOCKED)
                .channel(NotificationChannel.IN_APP)
                .referenceType(ENTITY_VAULT)
                .referenceId(mail.vaultId())
                .content(String.format(MSG_IN_APP, mail.vaultName(), mail.ownerName(),
                        EmailSender.formatDeadline(mail.claimDeadlineAt())))
                .status(NotificationStatus.SENT)
                .sentAt(LocalDateTime.now())
                .build());
    }

    private void recordFailure(UUID vaultId, String detail, Exception e) {
        log.warn("FR-16: vault {} - {}", vaultId, detail, e);
        auditFailure(vaultId, detail);
    }

    private void auditFailure(UUID vaultId, String detail) {
        auditLogService.log(AuditAction.BENEFICIARY_NOTIFY_FAILED, AuditResult.FAILURE, null, null,
                vaultId, ENTITY_VAULT, vaultId.toString(), detail);
    }

    // Dữ liệu cần để gửi mail, lấy xong trong pha 1 để pha 2 không phải chạm vào entity lazy
    private record MailData(UUID claimId, UUID vaultId, String email, String fullName, String ownerName,
                            String vaultName, LocalDateTime claimDeadlineAt, boolean accountActive,
                            UserStatus accountStatus, int attempt, List<String> executorEmails,
                            User beneficiary, boolean pendingRegistration) {
    }
}