package com.ltld.app.legacyvault.service.handoverservice;

import com.ltld.app.legacyvault.dto.assetverificationdto.AssetVerificationSummary;
import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.entity.VaultHandoverSnapshot;
import com.ltld.app.legacyvault.enums.AssetStatus;
import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.AssetVerificationMethod;
import com.ltld.app.legacyvault.enums.AssetVerificationStatus;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.enums.VerificationProvider;
import com.ltld.app.legacyvault.repository.DigitalAssetRepository;
import com.ltld.app.legacyvault.repository.VaultHandoverSnapshotRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.assetverificationservice.AssetVerificationService;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.utility.Sha256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HandoverSnapshotServiceImplTest {

    @Mock private VaultHandoverSnapshotRepository snapshotRepository;
    @Mock private VaultRepository vaultRepository;
    @Mock private DigitalAssetRepository digitalAssetRepository;
    @Mock private AssetVerificationService assetVerificationService;
    @Mock private AuditLogService auditLogService;
    @Mock private PlatformTransactionManager transactionManager;

    private HandoverSnapshotServiceImpl service;
    private Vault vault;
    private DigitalAsset assetA;
    private DigitalAsset assetB;

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new HandoverSnapshotServiceImpl(snapshotRepository, vaultRepository, digitalAssetRepository,
                assetVerificationService, auditLogService, transactionManager);
        User owner = User.builder().id(UUID.randomUUID()).email("owner@example.com").build();
        vault = Vault.builder().id(UUID.randomUUID()).owner(owner).name("V").status(VaultStatus.UNLOCKED).build();
        assetA = DigitalAsset.builder().id(UUID.fromString("00000000-0000-0000-0000-00000000000a")).vault(vault)
                .assetType(AssetType.EMAIL).assetName("Gmail").publicFieldsJson("{\"platform\":\"Gmail\"}")
                .status(AssetStatus.ACTIVE).build();
        assetB = DigitalAsset.builder().id(UUID.fromString("00000000-0000-0000-0000-00000000000b")).vault(vault)
                .assetType(AssetType.EMAIL).assetName("Outlook").publicFieldsJson("{\"platform\":\"Outlook\"}")
                .status(AssetStatus.ACTIVE).build();
    }

    private Map<UUID, AssetVerificationSummary> verification() {
        return Map.of(assetA.getId(), new AssetVerificationSummary(AssetVerificationStatus.VERIFIED,
                        AssetVerificationMethod.OTP_EMAIL, VerificationProvider.REAL,
                        LocalDateTime.of(2026, 10, 1, 9, 0), LocalDateTime.of(2027, 3, 30, 9, 0)),
                assetB.getId(), new AssetVerificationSummary(AssetVerificationStatus.SELF_DECLARED,
                        null, null, null, null));
    }

    @Test
    void ensureSnapshot_createsOnce_hashesDataAndAudits() {
        when(snapshotRepository.findByVaultId(vault.getId())).thenReturn(Optional.empty());
        when(vaultRepository.findById(vault.getId())).thenReturn(Optional.of(vault));
        when(digitalAssetRepository.findByVaultIdAndStatus(vault.getId(), AssetStatus.ACTIVE))
                .thenReturn(List.of(assetB, assetA));
        when(assetVerificationService.summarize(any())).thenReturn(verification());
        when(snapshotRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        Optional<HandoverSnapshotView> view = service.ensureSnapshot(vault.getId());

        ArgumentCaptor<VaultHandoverSnapshot> saved = ArgumentCaptor.forClass(VaultHandoverSnapshot.class);
        verify(snapshotRepository).saveAndFlush(saved.capture());
        assertThat(view).isPresent();
        assertThat(saved.getValue().getSnapshotHash()).isEqualTo(Sha256.hex(saved.getValue().getSnapshotData()));
        assertThat(saved.getValue().getAssetCount()).isEqualTo(2);
        assertThat(view.get().verification().get(assetA.getId()).status()).isEqualTo(AssetVerificationStatus.VERIFIED);
        assertThat(view.get().verification().get(assetB.getId()).status())
                .isEqualTo(AssetVerificationStatus.SELF_DECLARED);
        verify(auditLogService).log(eq(AuditAction.HANDOVER_SNAPSHOT_CREATED), eq(AuditResult.SUCCESS), isNull(),
                isNull(), eq(vault.getId()), eq("Vault"), eq(vault.getId().toString()),
                contains("sha256=" + saved.getValue().getSnapshotHash()));
    }

    @Test
    void buildData_sameContentSameHash_regardlessOfInputOrder() {
        String one = HandoverSnapshotServiceImpl.buildData(List.of(assetA, assetB), verification());
        String two = HandoverSnapshotServiceImpl.buildData(List.of(assetB, assetA), verification());

        assertThat(one).isEqualTo(two);
        assertThat(Sha256.hex(one)).isEqualTo(Sha256.hex(two));
    }

    @Test
    void ensureSnapshot_existing_returnedWithoutCreatingAgain() {
        String data = HandoverSnapshotServiceImpl.buildData(List.of(assetA), verification());
        VaultHandoverSnapshot existing = VaultHandoverSnapshot.builder().vault(vault).snapshotData(data)
                .snapshotHash(Sha256.hex(data)).assetCount(1).createdAt(LocalDateTime.now()).build();
        when(snapshotRepository.findByVaultId(vault.getId())).thenReturn(Optional.of(existing));

        Optional<HandoverSnapshotView> view = service.ensureSnapshot(vault.getId());

        assertThat(view).isPresent();
        verify(snapshotRepository, never()).saveAndFlush(any());
        verify(auditLogService, never()).log(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void ensureSnapshot_vaultStillActive_returnsEmptyAndSavesNothing() {
        vault.setStatus(VaultStatus.ACTIVE);
        when(snapshotRepository.findByVaultId(vault.getId())).thenReturn(Optional.empty());
        when(vaultRepository.findById(vault.getId())).thenReturn(Optional.of(vault));

        assertThat(service.ensureSnapshot(vault.getId())).isEmpty();
        verify(snapshotRepository, never()).saveAndFlush(any());
    }

    @Test
    void verifyIntegrity_detectsTamperedData() {
        String data = HandoverSnapshotServiceImpl.buildData(List.of(assetA), verification());
        VaultHandoverSnapshot good = VaultHandoverSnapshot.builder().vault(vault).snapshotData(data)
                .snapshotHash(Sha256.hex(data)).assetCount(1).build();
        VaultHandoverSnapshot tampered = VaultHandoverSnapshot.builder().vault(vault)
                .snapshotData(data.replace("Gmail", "Hacked")).snapshotHash(Sha256.hex(data)).assetCount(1).build();

        when(snapshotRepository.findByVaultId(vault.getId())).thenReturn(Optional.of(good), Optional.of(tampered));

        assertThat(service.verifyIntegrity(vault.getId())).isTrue();
        assertThat(service.verifyIntegrity(vault.getId())).isFalse();
    }
}
