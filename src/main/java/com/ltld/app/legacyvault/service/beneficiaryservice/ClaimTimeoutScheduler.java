package com.ltld.app.legacyvault.service.beneficiaryservice;

import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.repository.BeneficiaryClaimRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * BR-04/BR-05: Vault UNLOCKED quá hạn nhận (60 ngày) chuyển ARCHIVED_LOCKED, claim PENDING chuyển EXPIRED.
 * Mặc định BẬT; tắt bằng app.beneficiary.claim-expiry.enabled=false (dùng trong integration test).
 * Scheduling được bật ở DmsSchedulingConfig.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.beneficiary.claim-expiry.enabled", havingValue = "true", matchIfMissing = true)
public class ClaimTimeoutScheduler {

    static final int BATCH_SIZE = 50;
    static final int PURGE_RETENTION_YEARS = 2; // BR-05
    private static final String ENTITY_VAULT = "Vault";

    private final VaultRepository vaultRepository;
    private final BeneficiaryClaimRepository claimRepository;
    private final AuditLogService auditLogService;
    private final TransactionTemplate requiresNewTx;

    public ClaimTimeoutScheduler(VaultRepository vaultRepository,
                                 BeneficiaryClaimRepository claimRepository,
                                 AuditLogService auditLogService,
                                 PlatformTransactionManager transactionManager) {
        this.vaultRepository = vaultRepository;
        this.claimRepository = claimRepository;
        this.auditLogService = auditLogService;
        this.requiresNewTx = new TransactionTemplate(transactionManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Scheduled(fixedDelayString = "${app.beneficiary.claim-expiry-scan-delay:PT1H}")
    public void scan() {
        archiveExpiredVaults(LocalDateTime.now());
    }

    public void archiveExpiredVaults(LocalDateTime now) {
        List<UUID> vaultIds = vaultRepository.findExpiredUnlockedVaultIds(now, PageRequest.of(0, BATCH_SIZE));
        for (UUID vaultId : vaultIds) {
            try {
                Boolean archived = requiresNewTx.execute(status -> archive(vaultId, now, status));
                if (Boolean.TRUE.equals(archived)) {
                    // Ghi audit sau khi commit: actor là hệ thống (userId = null)
                    auditLogService.log(AuditAction.VAULT_ARCHIVED, AuditResult.SUCCESS, null, null,
                            vaultId, ENTITY_VAULT, vaultId.toString(), "claim timeout reached");
                }
            } catch (RuntimeException e) {
                log.error("BR-04: could not archive vault {}: {}", vaultId, e.getMessage());
            }
        }
    }

    // Thứ tự claim rồi vault giống closeVault để không bị deadlock
    private boolean archive(UUID vaultId, LocalDateTime now, TransactionStatus status) {
        claimRepository.markExpiredByVaultId(vaultId);
        int archived = vaultRepository.archiveIfExpired(vaultId, now, now.plusYears(PURGE_RETENTION_YEARS));
        if (archived == 0) {
            status.setRollbackOnly(); // vault đã đổi trạng thái (vd. vừa CLAIMED): hoàn tác cả việc đổi claim
            return false;
        }
        return true;
    }
}
