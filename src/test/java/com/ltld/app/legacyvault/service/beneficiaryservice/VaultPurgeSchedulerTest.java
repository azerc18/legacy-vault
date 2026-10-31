package com.ltld.app.legacyvault.service.beneficiaryservice;

import com.ltld.app.legacyvault.entity.LegalDocument;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.repository.AssetAccessRecordRepository;
import com.ltld.app.legacyvault.repository.AssetAllocationRepository;
import com.ltld.app.legacyvault.repository.BeneficiaryClaimRepository;
import com.ltld.app.legacyvault.repository.DigitalAssetRepository;
import com.ltld.app.legacyvault.repository.IdentityVerificationRepository;
import com.ltld.app.legacyvault.repository.LegalDocumentRepository;
import com.ltld.app.legacyvault.repository.VaultBeneficiaryRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.repository.VerificationRequestVaultRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.fileservice.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VaultPurgeSchedulerTest {

    @Mock private VaultRepository vaultRepository;
    @Mock private AssetAccessRecordRepository accessRecordRepository;
    @Mock private IdentityVerificationRepository identityVerificationRepository;
    @Mock private VerificationRequestVaultRepository requestVaultRepository;
    @Mock private LegalDocumentRepository legalDocumentRepository;
    @Mock private DigitalAssetRepository digitalAssetRepository;
    @Mock private BeneficiaryClaimRepository claimRepository;
    @Mock private VaultBeneficiaryRepository vaultBeneficiaryRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private AuditLogService auditLogService;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private AssetAllocationRepository assetAllocationRepository;

    private VaultPurgeScheduler scheduler;

    private final LocalDateTime now = LocalDateTime.of(2028, 10, 18, 12, 0);
    private final UUID vaultId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        scheduler = new VaultPurgeScheduler(vaultRepository, accessRecordRepository,
                identityVerificationRepository, requestVaultRepository, legalDocumentRepository,
                digitalAssetRepository, claimRepository, vaultBeneficiaryRepository, assetAllocationRepository,
                fileStorageService, auditLogService, transactionManager);
    }

    private Vault vault(UUID id, VaultStatus status, LocalDateTime purgeEligibleAt) {
        return Vault.builder().id(id).status(status).purgeEligibleAt(purgeEligibleAt).build();
    }

    private LegalDocument doc(String path) {
        return LegalDocument.builder().fileUrlEncrypted(path).build();
    }

    @Test
    void eligibleVault_deletesChildrenThenVault_filesAfterCommit_andAudits() {
        Vault vault = vault(vaultId, VaultStatus.ARCHIVED_LOCKED, now.minusDays(1));
        when(vaultRepository.findPurgeableVaultIds(eq(now), any(Pageable.class))).thenReturn(List.of(vaultId));
        when(vaultRepository.findByIdForUpdate(vaultId)).thenReturn(Optional.of(vault));
        when(legalDocumentRepository.findByVaultId(vaultId))
                .thenReturn(List.of(doc("/uploads/a.enc"), doc("/uploads/b.enc")));

        scheduler.purgeExpiredVaults(now);

        InOrder order = inOrder(accessRecordRepository, identityVerificationRepository, requestVaultRepository,
                legalDocumentRepository, digitalAssetRepository, claimRepository, vaultBeneficiaryRepository, assetAllocationRepository,
                vaultRepository, transactionManager, fileStorageService);
        order.verify(accessRecordRepository).deleteByVaultId(vaultId);
        order.verify(identityVerificationRepository).deleteByVaultId(vaultId);
        order.verify(requestVaultRepository).deleteByVaultId(vaultId);
        order.verify(legalDocumentRepository).deleteByVaultId(vaultId);
        order.verify(assetAllocationRepository).deleteByVaultId(vaultId);
        order.verify(digitalAssetRepository).deleteByVaultId(vaultId);
        order.verify(claimRepository).deleteByVaultId(vaultId);
        order.verify(vaultBeneficiaryRepository).deleteByVaultId(vaultId);
        order.verify(vaultRepository).delete(vault);
        order.verify(transactionManager).commit(any());
        order.verify(fileStorageService).deleteFile("/uploads/a.enc");
        order.verify(fileStorageService).deleteFile("/uploads/b.enc");
        verify(auditLogService).log(eq(AuditAction.VAULT_PURGED), eq(AuditResult.SUCCESS), isNull(), isNull(),
                eq(vaultId), eq("Vault"), eq(vaultId.toString()), contains("documentsDeleted=2"));
    }

    @Test
    void claimedVault_notPurged() {
        when(vaultRepository.findPurgeableVaultIds(eq(now), any(Pageable.class))).thenReturn(List.of(vaultId));
        when(vaultRepository.findByIdForUpdate(vaultId))
                .thenReturn(Optional.of(vault(vaultId, VaultStatus.CLAIMED, null)));

        scheduler.purgeExpiredVaults(now);

        verify(vaultRepository, never()).delete(any());
        verify(claimRepository, never()).deleteByVaultId(any());
        verify(fileStorageService, never()).deleteFile(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void notYetEligible_notPurged() {
        when(vaultRepository.findPurgeableVaultIds(eq(now), any(Pageable.class))).thenReturn(List.of(vaultId));
        when(vaultRepository.findByIdForUpdate(vaultId))
                .thenReturn(Optional.of(vault(vaultId, VaultStatus.ARCHIVED_LOCKED, now.plusDays(1))));

        scheduler.purgeExpiredVaults(now);

        verify(vaultRepository, never()).delete(any());
        verify(legalDocumentRepository, never()).findByVaultId(any());
        verify(fileStorageService, never()).deleteFile(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void oneVaultFails_othersStillProcessed() {
        UUID failing = UUID.randomUUID();
        Vault ok = vault(vaultId, VaultStatus.ARCHIVED_LOCKED, now.minusDays(1));
        when(vaultRepository.findPurgeableVaultIds(eq(now), any(Pageable.class)))
                .thenReturn(List.of(failing, vaultId));
        when(vaultRepository.findByIdForUpdate(failing)).thenThrow(new IllegalStateException("db down"));
        when(vaultRepository.findByIdForUpdate(vaultId)).thenReturn(Optional.of(ok));

        scheduler.purgeExpiredVaults(now);

        verify(vaultRepository).delete(ok);
        verify(auditLogService).log(eq(AuditAction.VAULT_PURGED), eq(AuditResult.SUCCESS), isNull(), isNull(),
                eq(vaultId), eq("Vault"), eq(vaultId.toString()), anyString());
        verify(auditLogService, never()).log(any(), any(), any(), any(), eq(failing), any(), any(), any());
    }

    @Test
    void deleteFails_transactionRolledBack_deletesNoFiles_noAudit() {
        Vault vault = vault(vaultId, VaultStatus.ARCHIVED_LOCKED, now.minusDays(1));
        when(vaultRepository.findPurgeableVaultIds(eq(now), any(Pageable.class))).thenReturn(List.of(vaultId));
        when(vaultRepository.findByIdForUpdate(vaultId)).thenReturn(Optional.of(vault));
        when(legalDocumentRepository.findByVaultId(vaultId)).thenReturn(List.of(doc("/uploads/a.enc")));
        when(claimRepository.deleteByVaultId(vaultId)).thenThrow(new IllegalStateException("fk"));

        scheduler.purgeExpiredVaults(now);

        verify(transactionManager).rollback(any());
        verify(fileStorageService, never()).deleteFile(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void noPurgeableVault_doesNothing() {
        when(vaultRepository.findPurgeableVaultIds(eq(now), any(Pageable.class))).thenReturn(List.of());

        scheduler.purgeExpiredVaults(now);

        verify(vaultRepository, never()).findByIdForUpdate(any());
        verifyNoInteractions(auditLogService);
    }
}