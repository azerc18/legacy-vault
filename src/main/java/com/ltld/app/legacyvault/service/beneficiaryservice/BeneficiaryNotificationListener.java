package com.ltld.app.legacyvault.service.beneficiaryservice;

import com.ltld.app.legacyvault.entity.BeneficiaryClaim;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.ClaimStatus;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.event.VaultsUnlockedEvent;
import com.ltld.app.legacyvault.repository.BeneficiaryClaimRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.utility.EmailSender;
import lombok.extern.slf4j.Slf4j;
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
 *  1. Chuẩn bị (giao dịch riêng): kiểm tra, tạo BeneficiaryClaim PENDING nếu chưa có, tăng notifyAttempts, commit.
 *  2. Gửi mail (ngoài giao dịch, đồng bộ): lỗi thì không ghi notifiedAt, ghi audit FAILED.
 *  3. Ghi nhận (giao dịch riêng): chỉ khi gửi thành công mới set notifiedAt và ghi audit SUCCESS.
 * Vault chưa gửi được sẽ được BeneficiaryNotificationRetryScheduler gửi lại, tối đa MAX_NOTIFY_ATTEMPTS lần.
 */
@Slf4j
@Component
public class BeneficiaryNotificationListener {

    // Tổng số lần thử gửi cho mỗi Vault (tính cả lần đầu), cùng ý tưởng giới hạn như FR-07
    static final int MAX_NOTIFY_ATTEMPTS = 5;
    // Giới hạn số Vault mỗi lượt quét để không giữ thread scheduler (dùng chung với FR-07) quá lâu
    static final int RETRY_BATCH_SIZE = 50;
    // Vault vừa mở khóa còn thuộc về listener sự kiện; scheduler bỏ qua để không gửi trùng
    static final Duration RETRY_GRACE = Duration.ofMinutes(5);

    private static final String ENTITY_VAULT = "Vault";
    private static final String OWNER_FALLBACK = "chủ hồ sơ";

    private final VaultRepository vaultRepository;
    private final BeneficiaryClaimRepository claimRepository;
    private final EmailSender emailSender;
    private final AuditLogService auditLogService;
    private final TransactionTemplate requiresNewTx;

    public BeneficiaryNotificationListener(VaultRepository vaultRepository,
                                           BeneficiaryClaimRepository claimRepository,
                                           EmailSender emailSender,
                                           AuditLogService auditLogService,
                                           PlatformTransactionManager transactionManager) {
        this.vaultRepository = vaultRepository;
        this.claimRepository = claimRepository;
        this.emailSender = emailSender;
        this.auditLogService = auditLogService;
        this.requiresNewTx = new TransactionTemplate(transactionManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // @Async trên listener (không phải trên hàm gửi mail): request FR-14 không phải chờ SMTP,
    // nhưng lỗi gửi vẫn được bắt và ghi nhận ngay trong luồng này.
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onVaultsUnlocked(VaultsUnlockedEvent event) {
        event.vaultIds().forEach(this::processVault);
    }

    /** Lượt quét gửi lại: Vault UNLOCKED còn hạn mà chưa gửi được thông báo. */
    public void retryPendingNotifications(LocalDateTime now) {
        List<UUID> vaultIds = vaultRepository.findVaultIdsToNotify(
                now, now.minus(RETRY_GRACE), MAX_NOTIFY_ATTEMPTS);
        vaultIds.stream().limit(RETRY_BATCH_SIZE).forEach(this::processVault);
    }

    private void processVault(UUID vaultId) {
        final MailData mail = tryPrepare(vaultId);
        if (mail == null) {
            return;
        }

        // Pha 2: gửi mail ngoài mọi giao dịch
        try {
            emailSender.sendVaultUnlockedNotification(mail.email(), mail.fullName(), mail.ownerName(),
                    mail.vaultName(), mail.claimDeadlineAt(), mail.accountActive());
        } catch (RuntimeException e) {
            recordFailure(vaultId, "recipient=" + mail.email() + ", send failed (attempt "
                    + mail.attempt() + "/" + MAX_NOTIFY_ATTEMPTS + "): " + e.getClass().getSimpleName(), e);
            return;
        }

        // Pha 3: gửi thành công mới ghi notifiedAt
        final UUID claimId = mail.claimId();
        try {
            requiresNewTx.executeWithoutResult(status -> markNotified(claimId));
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

    private MailData tryPrepare(UUID vaultId) {
        try {
            return requiresNewTx.execute(status -> prepare(vaultId));
        } catch (RuntimeException e) {
            recordFailure(vaultId, "prepare failed: " + e.getClass().getSimpleName(), e);
            return null;
        }
    }

    // Pha 1: trả về null nếu không cần/không thể gửi
    private MailData prepare(UUID vaultId) {
        Vault vault = vaultRepository.findById(vaultId).orElse(null);
        if (vault == null || vault.getStatus() != VaultStatus.UNLOCKED) {
            log.warn("FR-16: Skip vault {} (not found or not UNLOCKED)", vaultId);
            return null;
        }

        // SRS FR-16 precondition: Vault phải có Beneficiary được gán
        User beneficiary = vault.getBeneficiary();
        if (beneficiary == null) {
            auditFailure(vaultId, "no beneficiary assigned");
            return null;
        }
        // FR-14 luôn set claimDeadlineAt khi mở khóa; thiếu nghĩa là dữ liệu lỗi, không tự đoán hạn
        if (vault.getClaimDeadlineAt() == null) {
            auditFailure(vaultId, "vault has no claimDeadlineAt");
            return null;
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
        if (claim.getNotifyAttempts() >= MAX_NOTIFY_ATTEMPTS) {
            log.warn("FR-16: Vault {} reached {} notify attempts, giving up", vaultId, MAX_NOTIFY_ATTEMPTS);
            return null;
        }

        // Đếm lượt thử ngay ở pha này: nếu tiến trình chết giữa chừng thì lượt đó vẫn được tính
        claim.setNotifyAttempts(claim.getNotifyAttempts() + 1);
        claimRepository.save(claim);

        User owner = vault.getOwner();
        String ownerName = owner != null && owner.getFullName() != null && !owner.getFullName().isBlank()
                ? owner.getFullName() : OWNER_FALLBACK;

        return new MailData(claim.getId(), vaultId, beneficiary.getEmail(), beneficiary.getFullName(),
                ownerName, vault.getName(), claim.getClaimDeadlineAt(),
                beneficiary.getStatus() != UserStatus.PENDING,   // nhánh 2a: PENDING = chưa kích hoạt
                beneficiary.getStatus(), claim.getNotifyAttempts());
    }

    // Pha 3
    private void markNotified(UUID claimId) {
        claimRepository.findById(claimId).ifPresent(claim -> {
            claim.setNotifiedAt(LocalDateTime.now());
            claimRepository.save(claim);
        });
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
                            UserStatus accountStatus, int attempt) {
    }
}