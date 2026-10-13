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
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * FR-16: khi Legal Verifier ký mở khóa (FR-14 phát VaultsUnlockedEvent), gửi thông báo cho Beneficiary
 * của từng Vault, đồng thời tạo BeneficiaryClaim (PENDING) và ghi notifiedAt.
 * Chạy sau khi giao dịch FR-14 đã commit, nên lỗi gửi thông báo không làm hỏng việc mở khóa.
 */
@Slf4j
@Component
public class BeneficiaryNotificationListener {

    private static final int CLAIM_TIMEOUT_DAYS = 60;

    private final VaultRepository vaultRepository;
    private final BeneficiaryClaimRepository claimRepository;
    private final EmailSender emailSender;
    private final AuditLogService auditLogService;
    // Mỗi Vault xử lý trong giao dịch riêng: một Vault lỗi không kéo theo các Vault còn lại
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

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onVaultsUnlocked(VaultsUnlockedEvent event) {
        for (UUID vaultId : event.vaultIds()) {
            try {
                requiresNewTx.executeWithoutResult(status -> notifyBeneficiary(vaultId));
            } catch (Exception e) {
                // Không ném tiếp: FR-14 đã hoàn tất, chỉ ghi log và audit để đối soát
                log.error("FR-16: Failed to notify beneficiary for vault {}", vaultId, e);
                auditLogService.log(AuditAction.BENEFICIARY_NOTIFY_FAILED, AuditResult.FAILURE,
                        null, null, "Vault", vaultId.toString(), "notification failed");
            }
        }
    }

    private void notifyBeneficiary(UUID vaultId) {
        Vault vault = vaultRepository.findById(vaultId).orElse(null);
        if (vault == null || vault.getStatus() != VaultStatus.UNLOCKED) {
            log.warn("FR-16: Skip vault {} (not found or not UNLOCKED)", vaultId);
            return;
        }

        // SRS FR-16 precondition: Vault phải có Beneficiary được gán
        User beneficiary = vault.getBeneficiary();
        if (beneficiary == null) {
            log.warn("FR-16: Vault {} has no beneficiary assigned", vaultId);
            auditLogService.log(AuditAction.BENEFICIARY_NOTIFY_FAILED, AuditResult.FAILURE,
                    null, null, "Vault", vaultId.toString(), "no beneficiary assigned");
            return;
        }

        // Tạo claim PENDING nếu chưa có; initializeClaim (FR-16 API) sẽ dùng lại claim này
        BeneficiaryClaim claim = claimRepository.findByVaultId(vaultId)
                .orElseGet(() -> claimRepository.save(BeneficiaryClaim.builder()
                        .vault(vault)
                        .beneficiary(beneficiary)
                        .status(ClaimStatus.PENDING)
                        .claimDeadlineAt(resolveDeadline(vault))
                        .build()));

        // Chống gửi trùng nếu event bị phát lại
        if (claim.getNotifiedAt() != null) {
            log.info("FR-16: Vault {} already notified, skip", vaultId);
            return;
        }

        claim.setNotifiedAt(LocalDateTime.now());
        claimRepository.save(claim);

        // SRS FR-16 nhánh 2a: tài khoản chưa kích hoạt (PENDING) thì kèm lời nhắc đăng ký/xác minh
        boolean accountActive = beneficiary.getStatus() != UserStatus.PENDING;
        emailSender.sendVaultUnlockedNotification(
                beneficiary.getEmail(), beneficiary.getFullName(), claim.getClaimDeadlineAt(), accountActive);

        auditLogService.log(AuditAction.BENEFICIARY_NOTIFIED, AuditResult.SUCCESS,
                null, beneficiary.getEmail(), "Vault", vaultId.toString(),
                "accountStatus=" + beneficiary.getStatus());
    }

    // Cùng quy tắc với BeneficiaryServiceImpl.resolveDeadline
    private LocalDateTime resolveDeadline(Vault vault) {
        if (vault.getClaimDeadlineAt() != null) {
            return vault.getClaimDeadlineAt();
        }
        LocalDateTime base = vault.getUnlockedAt() != null ? vault.getUnlockedAt() : LocalDateTime.now();
        return base.plusDays(CLAIM_TIMEOUT_DAYS);
    }
}