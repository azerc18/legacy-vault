package com.ltld.app.legacyvault.service.beneficiaryservice;

import com.ltld.app.legacyvault.entity.LegalDocument;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.repository.BeneficiaryClaimRepository;
import com.ltld.app.legacyvault.repository.DigitalAssetRepository;
import com.ltld.app.legacyvault.repository.LegalDocumentRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.fileservice.FileStorageService;
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
import java.util.Objects;
import java.util.UUID;

/**
 * BR-04: khi lưu trữ, xóa nội dung tài sản (chỉ giữ metadata) và xóa file tài liệu pháp lý sau commit;
 * dòng LegalDocument và đường dẫn file được giữ lại để BR-05 dọn lại nếu lần xóa này thất bại.
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
    private final DigitalAssetRepository digitalAssetRepository;
    private final LegalDocumentRepository legalDocumentRepository;
    private final FileStorageService fileStorageService;
    private final AuditLogService auditLogService;
    private final TransactionTemplate requiresNewTx;

    public ClaimTimeoutScheduler(VaultRepository vaultRepository,
                                 BeneficiaryClaimRepository claimRepository,
                                 DigitalAssetRepository digitalAssetRepository,
                                 LegalDocumentRepository legalDocumentRepository,
                                 FileStorageService fileStorageService,
                                 AuditLogService auditLogService,
                                 PlatformTransactionManager transactionManager) {
        this.vaultRepository = vaultRepository;
        this.claimRepository = claimRepository;
        this.digitalAssetRepository = digitalAssetRepository;
        this.legalDocumentRepository = legalDocumentRepository;
        this.fileStorageService = fileStorageService;
        this.auditLogService = auditLogService;
        this.requiresNewTx = new TransactionTemplate(transactionManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Scheduled(fixedDelayString = "${app.beneficiary.claim-expiry-scan-delay:PT1H}")
    public void scan() {
        archiveExpiredVaults(LocalDateTime.now());
    }

    // Kết quả một lần lưu trữ thành công: số tài sản đã xóa nội dung và đường dẫn file cần xóa sau commit
    private record ArchiveResult(int assetsCleared, List<String> filePaths) {
    }

    public void archiveExpiredVaults(LocalDateTime now) {
        List<UUID> vaultIds = vaultRepository.findExpiredUnlockedVaultIds(now, PageRequest.of(0, BATCH_SIZE));
        for (UUID vaultId : vaultIds) {
            try {
                ArchiveResult result = requiresNewTx.execute(status -> archive(vaultId, now, status));
                if (result != null) {
                    // Đến đây giao dịch đã commit: xóa file vật lý rồi ghi audit (actor là hệ thống, userId = null)
                    deleteFiles(vaultId, result.filePaths());
                    auditLogService.log(AuditAction.VAULT_ARCHIVED, AuditResult.SUCCESS, null, null,
                            vaultId, ENTITY_VAULT, vaultId.toString(),
                            "claim timeout reached, assetsCleared=" + result.assetsCleared()
                                    + ", documentsCleared=" + result.filePaths().size());
                }
            } catch (RuntimeException e) {
                log.error("BR-04: could not archive vault {}: {}", vaultId, e.getMessage());
            }
        }
    }

    // Thứ tự claim rồi vault giống closeVault để không bị deadlock.
    // Trả về null nếu không lưu trữ (giao dịch bị rollback), nên phần xóa nội dung chỉ chạy sau nhánh archived == 0.
    private ArchiveResult archive(UUID vaultId, LocalDateTime now, TransactionStatus status) {
        claimRepository.markExpiredByVaultId(vaultId);
        int archived = vaultRepository.archiveIfExpired(vaultId, now, now.plusYears(PURGE_RETENTION_YEARS));
        if (archived == 0) {
            status.setRollbackOnly(); // vault đã đổi trạng thái (vd. vừa CLAIMED): hoàn tác cả việc đổi claim
            return null;
        }

        // BR-04: chỉ giữ metadata. Không xóa dòng LegalDocument và không xóa fileUrlEncrypted trong DB
        // để BR-05 còn đường dẫn dọn lại nếu lần xóa file bên dưới thất bại.
        int assetsCleared = digitalAssetRepository.wipeContentByVaultId(vaultId, now);
        List<String> filePaths = legalDocumentRepository.findByVaultId(vaultId).stream()
                .map(LegalDocument::getFileUrlEncrypted)
                .filter(Objects::nonNull)
                .toList();
        return new ArchiveResult(assetsCleared, filePaths);
    }

    // Lỗi xóa một file không được chặn việc ghi audit hay các file khác
    private void deleteFiles(UUID vaultId, List<String> filePaths) {
        for (String path : filePaths) {
            try {
                fileStorageService.deleteFile(path);
            } catch (RuntimeException e) {
                log.warn("BR-04: vault {} - could not delete a document file: {}", vaultId, e.getMessage());
            }
        }
    }

}
