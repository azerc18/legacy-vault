package com.ltld.app.legacyvault.service.beneficiaryservice;

import com.ltld.app.legacyvault.entity.LegalDocument;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.repository.*;
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
 * BR-05: Vault ARCHIVED_LOCKED tới purgeEligibleAt thì xóa vĩnh viễn (con trước, cha sau), giữ AuditLog.
 * Mặc định BẬT; tắt bằng app.beneficiary.vault-purge.enabled=false (dùng trong integration test).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.beneficiary.vault-purge.enabled", havingValue = "true", matchIfMissing = true)
public class VaultPurgeScheduler {

    static final int BATCH_SIZE = 50;
    private static final String ENTITY_VAULT = "Vault";

    private final VaultRepository vaultRepository;
    private final AssetAccessRecordRepository accessRecordRepository;
    private final IdentityVerificationRepository identityVerificationRepository;
    private final VerificationRequestVaultRepository requestVaultRepository;
    private final LegalDocumentRepository legalDocumentRepository;
    private final DigitalAssetRepository digitalAssetRepository;
    private final BeneficiaryClaimRepository claimRepository;
    private final VaultBeneficiaryRepository vaultBeneficiaryRepository;
    private final FileStorageService fileStorageService;
    private final AuditLogService auditLogService;
    private final TransactionTemplate requiresNewTx;

    public VaultPurgeScheduler(VaultRepository vaultRepository,
                               AssetAccessRecordRepository accessRecordRepository,
                               IdentityVerificationRepository identityVerificationRepository,
                               VerificationRequestVaultRepository requestVaultRepository,
                               LegalDocumentRepository legalDocumentRepository,
                               DigitalAssetRepository digitalAssetRepository,
                               BeneficiaryClaimRepository claimRepository,
                               VaultBeneficiaryRepository vaultBeneficiaryRepository,
                               FileStorageService fileStorageService,
                               AuditLogService auditLogService,
                               PlatformTransactionManager transactionManager) {
        this.vaultRepository = vaultRepository;
        this.accessRecordRepository = accessRecordRepository;
        this.identityVerificationRepository = identityVerificationRepository;
        this.requestVaultRepository = requestVaultRepository;
        this.legalDocumentRepository = legalDocumentRepository;
        this.digitalAssetRepository = digitalAssetRepository;
        this.claimRepository = claimRepository;
        this.vaultBeneficiaryRepository = vaultBeneficiaryRepository;
        this.fileStorageService = fileStorageService;
        this.auditLogService = auditLogService;
        this.requiresNewTx = new TransactionTemplate(transactionManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Scheduled(fixedDelayString = "${app.beneficiary.vault-purge-scan-delay:PT6H}")
    public void scan() {
        purgeExpiredVaults(LocalDateTime.now());
    }

    public void purgeExpiredVaults(LocalDateTime now) {
        List<UUID> vaultIds = vaultRepository.findPurgeableVaultIds(now, PageRequest.of(0, BATCH_SIZE));
        for (UUID vaultId : vaultIds) {
            try {
                List<String> filePaths = requiresNewTx.execute(status -> purge(vaultId, now, status));
                if (filePaths != null) {
                    // Giao dịch đã commit: dọn file (idempotent), rồi ghi audit (actor là hệ thống)
                    for (String path : filePaths) {
                        try {
                            fileStorageService.deleteFile(path);
                        } catch (RuntimeException e) {
                            log.warn("BR-05: vault {} - could not delete a file: {}", vaultId, e.getMessage());
                        }
                    }
                    auditLogService.log(AuditAction.VAULT_PURGED, AuditResult.SUCCESS, null, null,
                            vaultId, ENTITY_VAULT, vaultId.toString(),
                            "purge retention reached, documentsDeleted=" + filePaths.size());
                }
            } catch (RuntimeException e) {
                log.error("BR-05: could not purge vault {}: {}", vaultId, e.getMessage());
            }
        }
    }

    // Trả về null nếu bỏ qua (không xóa gì); ngược lại trả đường dẫn file để xóa sau commit
    private List<String> purge(UUID vaultId, LocalDateTime now, TransactionStatus status) {
        Vault vault = vaultRepository.findByIdForUpdate(vaultId).orElse(null);
        if (vault == null || vault.getStatus() != VaultStatus.ARCHIVED_LOCKED
                || vault.getPurgeEligibleAt() == null || vault.getPurgeEligibleAt().isAfter(now)) {
            status.setRollbackOnly();
            return null;
        }
        // Phải lấy đường dẫn TRƯỚC khi xóa dòng legal_documents
        List<String> filePaths = legalDocumentRepository.findByVaultId(vaultId).stream()
                .map(LegalDocument::getFileUrlEncrypted)
                .filter(Objects::nonNull)
                .toList();

        // Con trước, cha sau (theo FK)
        accessRecordRepository.deleteByVaultId(vaultId);
        identityVerificationRepository.deleteByVaultId(vaultId);
        requestVaultRepository.deleteByVaultId(vaultId);
        legalDocumentRepository.deleteByVaultId(vaultId);
        digitalAssetRepository.deleteByVaultId(vaultId);
        claimRepository.deleteByVaultId(vaultId);
        vaultBeneficiaryRepository.deleteByVaultId(vaultId);
        vaultRepository.delete(vault);
        return filePaths;
    }
}