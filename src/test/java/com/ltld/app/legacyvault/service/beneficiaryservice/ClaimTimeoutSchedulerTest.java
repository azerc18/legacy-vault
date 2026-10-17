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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClaimTimeoutSchedulerTest {

    @Mock private VaultRepository vaultRepository;
    @Mock private BeneficiaryClaimRepository claimRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private DigitalAssetRepository digitalAssetRepository;
    @Mock private LegalDocumentRepository legalDocumentRepository;
    @Mock private FileStorageService fileStorageService;

    private ClaimTimeoutScheduler scheduler;

    private final LocalDateTime now = LocalDateTime.of(2026, 10, 15, 12, 0);
    private final UUID vaultId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        scheduler = new ClaimTimeoutScheduler(vaultRepository, claimRepository, digitalAssetRepository,
                legalDocumentRepository, fileStorageService, auditLogService, transactionManager);
    }

    @Test
    void expiredVault_isArchivedClaimExpiredAndAudited() {
        when(vaultRepository.findExpiredUnlockedVaultIds(eq(now), any(Pageable.class))).thenReturn(List.of(vaultId));
        when(vaultRepository.archiveIfExpired(vaultId, now, now.plusYears(2))).thenReturn(1);

        scheduler.archiveExpiredVaults(now);

        verify(claimRepository).markExpiredByVaultId(vaultId);
        verify(auditLogService).log(eq(AuditAction.VAULT_ARCHIVED), eq(AuditResult.SUCCESS), isNull(), isNull(),
                eq(vaultId), eq("Vault"), eq(vaultId.toString()), anyString());
    }

    @Test
    void vaultChangedMeanwhile_noAudit() {
        when(vaultRepository.findExpiredUnlockedVaultIds(eq(now), any(Pageable.class))).thenReturn(List.of(vaultId));
        when(vaultRepository.archiveIfExpired(vaultId, now, now.plusYears(2))).thenReturn(0);

        scheduler.archiveExpiredVaults(now);

        verifyNoInteractions(auditLogService);
    }

    @Test
    void oneVaultFails_othersStillProcessed() {
        UUID failing = UUID.randomUUID();
        when(vaultRepository.findExpiredUnlockedVaultIds(eq(now), any(Pageable.class)))
                .thenReturn(List.of(failing, vaultId));
        when(claimRepository.markExpiredByVaultId(failing)).thenThrow(new IllegalStateException("db down"));
        when(vaultRepository.archiveIfExpired(vaultId, now, now.plusYears(2))).thenReturn(1);

        scheduler.archiveExpiredVaults(now);

        verify(auditLogService).log(eq(AuditAction.VAULT_ARCHIVED), eq(AuditResult.SUCCESS), isNull(), isNull(),
                eq(vaultId), eq("Vault"), eq(vaultId.toString()), anyString());
    }

    private LegalDocument doc(String path) {
        return LegalDocument.builder().fileUrlEncrypted(path).build();
    }

    @Test
    void expiredVault_wipesAssets_deletesFilesAfterCommit_andKeepsDocumentRows() {
        when(vaultRepository.findExpiredUnlockedVaultIds(eq(now), any(Pageable.class))).thenReturn(List.of(vaultId));
        when(vaultRepository.archiveIfExpired(vaultId, now, now.plusYears(2))).thenReturn(1);
        when(digitalAssetRepository.wipeContentByVaultId(vaultId, now)).thenReturn(3);
        when(legalDocumentRepository.findByVaultId(vaultId))
                .thenReturn(List.of(doc("/uploads/a.enc"), doc("/uploads/b.enc")));

        scheduler.archiveExpiredVaults(now);

        InOrder order = inOrder(transactionManager, fileStorageService);
        order.verify(transactionManager).commit(any());
        order.verify(fileStorageService).deleteFile("/uploads/a.enc");
        order.verify(fileStorageService).deleteFile("/uploads/b.enc");
        verify(legalDocumentRepository, never()).delete(any());
        verify(legalDocumentRepository, never()).deleteAll(any());
        verify(legalDocumentRepository, never()).deleteAllInBatch(any());
        verify(auditLogService).log(eq(AuditAction.VAULT_ARCHIVED), eq(AuditResult.SUCCESS), isNull(), isNull(),
                eq(vaultId), eq("Vault"), eq(vaultId.toString()),
                contains("assetsCleared=3, documentsCleared=2"));
    }

    @Test
    void vaultChangedMeanwhile_wipesNothing_deletesNoFiles() {
        when(vaultRepository.findExpiredUnlockedVaultIds(eq(now), any(Pageable.class))).thenReturn(List.of(vaultId));
        when(vaultRepository.archiveIfExpired(vaultId, now, now.plusYears(2))).thenReturn(0);

        scheduler.archiveExpiredVaults(now);

        verify(digitalAssetRepository, never()).wipeContentByVaultId(any(), any());
        verify(legalDocumentRepository, never()).findByVaultId(any());
        verify(fileStorageService, never()).deleteFile(any());
    }

    @Test
    void wipeFails_transactionRolledBack_deletesNoFiles_noAudit() {
        when(vaultRepository.findExpiredUnlockedVaultIds(eq(now), any(Pageable.class))).thenReturn(List.of(vaultId));
        when(vaultRepository.archiveIfExpired(vaultId, now, now.plusYears(2))).thenReturn(1);
        when(digitalAssetRepository.wipeContentByVaultId(vaultId, now)).thenThrow(new IllegalStateException("db down"));

        scheduler.archiveExpiredVaults(now);

        verify(transactionManager).rollback(any());
        verify(fileStorageService, never()).deleteFile(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void deleteFileFails_otherFilesStillDeleted_andVaultStillAudited() {
        when(vaultRepository.findExpiredUnlockedVaultIds(eq(now), any(Pageable.class))).thenReturn(List.of(vaultId));
        when(vaultRepository.archiveIfExpired(vaultId, now, now.plusYears(2))).thenReturn(1);
        when(legalDocumentRepository.findByVaultId(vaultId))
                .thenReturn(List.of(doc("/uploads/a.enc"), doc("/uploads/b.enc")));
        doThrow(new IllegalStateException("io")).when(fileStorageService).deleteFile("/uploads/a.enc");

        scheduler.archiveExpiredVaults(now);

        verify(fileStorageService).deleteFile("/uploads/b.enc");
        verify(auditLogService).log(eq(AuditAction.VAULT_ARCHIVED), eq(AuditResult.SUCCESS), isNull(), isNull(),
                eq(vaultId), eq("Vault"), eq(vaultId.toString()), anyString());
    }
}
