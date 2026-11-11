package com.ltld.app.legacyvault.service.readinessservice;

import com.ltld.app.legacyvault.dto.readinessdto.ReadinessItemResponse;
import com.ltld.app.legacyvault.dto.readinessdto.ReadinessResponse;
import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.entity.DmsConfig;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.AssetStatus;
import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.DmsStatus;
import com.ltld.app.legacyvault.enums.DocumentType;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.ExecutorType;
import com.ltld.app.legacyvault.enums.TransferMechanism;
import com.ltld.app.legacyvault.repository.AssetAllocationRepository;
import com.ltld.app.legacyvault.repository.DigitalAssetRepository;
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import com.ltld.app.legacyvault.repository.LegalDocumentRepository;
import com.ltld.app.legacyvault.repository.VaultBeneficiaryRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.assetverificationservice.AssetVerificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OwnerReadinessServiceImplTest {

    @Mock private VaultRepository vaultRepository;
    @Mock private DigitalAssetRepository digitalAssetRepository;
    @Mock private VaultBeneficiaryRepository vaultBeneficiaryRepository;
    @Mock private AssetAllocationRepository assetAllocationRepository;
    @Mock private LegalDocumentRepository legalDocumentRepository;
    @Mock private ExecutorAssignmentRepository executorAssignmentRepository;
    @Mock private DmsConfigRepository dmsConfigRepository;
    @Mock private AssetVerificationService assetVerificationService;
    @Mock private com.ltld.app.legacyvault.repository.UserRepository userRepository;

    @InjectMocks
    private OwnerReadinessServiceImpl readinessService;

    private UUID ownerId;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
    }

    // ---------- helpers ----------

    private static Vault vault(int daysAgo) {
        return Vault.builder()
                .id(UUID.randomUUID())
                .name("Vault")
                .createdAt(LocalDateTime.now().minusDays(daysAgo))
                .updatedAt(LocalDateTime.now().minusDays(daysAgo))
                .build();
    }

    private static DigitalAsset asset(Vault vault, AssetType type) {
        return DigitalAsset.builder()
                .id(UUID.randomUUID())
                .vault(vault)
                .assetType(type)
                .assetName(type.name())
                .status(AssetStatus.ACTIVE)
                .createdAt(LocalDateTime.now().minusDays(5))
                .build();
    }

    private static ExecutorAssignment executor(ExecutorType type) {
        return ExecutorAssignment.builder().executorType(type).status(ExecutorStatus.INACTIVE).build();
    }

    private static DmsConfig activeDms() {
        return DmsConfig.builder()
                .status(DmsStatus.ACTIVE)
                .checkinIntervalDays(30)
                .gracePeriodDays(7)
                .reminderCount(3)
                .lastCheckinAt(LocalDateTime.now().minusDays(1))
                .build();
    }

    private static ReadinessItemResponse item(ReadinessResponse res, String key) {
        return res.getItems().stream().filter(i -> i.getKey().equals(key)).findFirst().orElseThrow();
    }

    /** Stub đủ các truy vấn theo lô của một Owner có vault (mặc định đã có người thụ hưởng dự phòng). */
    private void stubEstate(List<Vault> vaults, List<DigitalAsset> activeAssets, List<UUID> vaultsWithBeneficiary,
                            List<UUID> allocatedAssetIds, List<UUID> vaultsWithWill) {
        stubEstate(vaults, activeAssets, vaultsWithBeneficiary, allocatedAssetIds, vaultsWithWill, true);
    }

    private void stubEstate(List<Vault> vaults, List<DigitalAsset> activeAssets, List<UUID> vaultsWithBeneficiary,
                            List<UUID> allocatedAssetIds, List<UUID> vaultsWithWill, boolean hasContingent) {
        when(vaultRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId)).thenReturn(vaults);
        when(digitalAssetRepository.findByVaultIdInAndStatus(vaults.stream().map(Vault::getId).toList(),
                AssetStatus.ACTIVE)).thenReturn(activeAssets);
        when(vaultBeneficiaryRepository.findVaultIdsWithBeneficiaryByOwnerId(ownerId)).thenReturn(vaultsWithBeneficiary);
        if (!activeAssets.isEmpty()) {
            when(assetAllocationRepository.findAllocatedActiveAssetIdsByOwnerId(ownerId)).thenReturn(allocatedAssetIds);
            when(assetAllocationRepository.existsContingentForOwner(ownerId)).thenReturn(hasContingent);
        }
        when(legalDocumentRepository.findVaultIdsWithDocumentTypeByOwnerId(ownerId, DocumentType.WILL))
                .thenReturn(vaultsWithWill);
        when(executorAssignmentRepository.findAllByOwnerId(ownerId))
                .thenReturn(List.of(executor(ExecutorType.PRIMARY), executor(ExecutorType.BACKUP)));
        when(dmsConfigRepository.findByOwnerId(ownerId)).thenReturn(Optional.of(activeDms()));
    }

    // ---------- tests ----------

    @Test
    @DisplayName("F1: Owner mới chưa cấu hình gì -> 0/100, AT_RISK, 7 tiêu chí, không gọi truy vấn theo vault")
    void evaluateReadiness_emptyEstate_returnsZeroAndAtRisk() {
        when(vaultRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId)).thenReturn(List.of());
        when(executorAssignmentRepository.findAllByOwnerId(ownerId)).thenReturn(List.of());
        when(dmsConfigRepository.findByOwnerId(ownerId)).thenReturn(Optional.empty());

        ReadinessResponse res = readinessService.evaluateReadiness(ownerId);

        assertEquals(0, res.getScore());
        assertEquals(100, res.getMaxScore());
        assertEquals("AT_RISK", res.getGrade());
        assertEquals(0, res.getCompletedItems());
        assertEquals(8, res.getTotalItems());
        assertFalse(item(res, "IDENTITY_VERIFIED").isDone());
        assertEquals(0, item(res, "IDENTITY_VERIFIED").getPoints());
        assertEquals("/owner/vaults/new", item(res, "MASTER_VAULT_AND_ASSET").getFixUrl());
        verifyNoInteractions(digitalAssetRepository, vaultBeneficiaryRepository,
                assetAllocationRepository, legalDocumentRepository);
    }

    @Test
    @DisplayName("F1: vault 2 người thụ hưởng, mọi tài sản ACTIVE đã phân bổ, đủ điều kiện khác -> 100/100 EXCELLENT")
    void evaluateReadiness_vaultWithTwoBeneficiaries_allAllocated_returns100() {
        Vault v = vault(2);
        DigitalAsset bank = asset(v, AssetType.BANK_ACCOUNT);       // hồ sơ pháp lý -> cần WILL
        DigitalAsset crypto = asset(v, AssetType.CRYPTO_WALLET);
        stubEstate(List.of(v), List.of(bank, crypto), List.of(v.getId()),
                List.of(bank.getId(), crypto.getId()), List.of(v.getId()));
        when(userRepository.findById(ownerId)).thenReturn(java.util.Optional.of(
                com.ltld.app.legacyvault.entity.User.builder().id(ownerId)
                        .kycLevel(com.ltld.app.legacyvault.enums.KycLevel.BASIC).build()));

        ReadinessResponse res = readinessService.evaluateReadiness(ownerId);

        assertEquals(100, res.getScore());
        assertEquals("EXCELLENT", res.getGrade());
        assertEquals(8, res.getCompletedItems());
        assertTrue(res.getItems().stream().allMatch(ReadinessItemResponse::isDone));
        assertEquals("items.assetBeneficiaryAllocated", item(res, "ASSET_BENEFICIARY_ALLOCATED").getTitleKey());
    }

    @Test
    @DisplayName("F1: vault có tài sản nhưng chưa có người thụ hưởng -> tiêu chí phân bổ chưa đạt, fixUrl về vault đó")
    void evaluateReadiness_vaultWithoutBeneficiary_notAllocated() {
        Vault ok = vault(1);
        Vault missing = vault(3);
        DigitalAsset a1 = asset(ok, AssetType.SOCIAL_MEDIA);
        DigitalAsset a2 = asset(missing, AssetType.SOCIAL_MEDIA);
        stubEstate(List.of(ok, missing), List.of(a1, a2), List.of(ok.getId()),
                List.of(a1.getId()), List.of(ok.getId()));

        ReadinessResponse res = readinessService.evaluateReadiness(ownerId);

        ReadinessItemResponse alloc = item(res, "ASSET_BENEFICIARY_ALLOCATED");
        assertFalse(alloc.isDone());
        assertNull(alloc.getFixUrl());   // m1: FE chưa có giao diện phân bổ
        assertEquals(85, res.getScore());
    }

    @Test
    @DisplayName("F1: vault có người thụ hưởng nhưng còn tài sản ACTIVE chưa có dòng phân bổ -> chưa đạt")
    void evaluateReadiness_unallocatedAsset_notAllocated() {
        Vault older = vault(5);
        Vault newer = vault(1);
        DigitalAsset allocated = asset(newer, AssetType.EMAIL);
        DigitalAsset unallocated = asset(older, AssetType.EMAIL);
        stubEstate(List.of(newer, older), List.of(allocated, unallocated), List.of(newer.getId(), older.getId()),
                List.of(allocated.getId()), List.of(newer.getId()));

        ReadinessResponse res = readinessService.evaluateReadiness(ownerId);

        ReadinessItemResponse alloc = item(res, "ASSET_BENEFICIARY_ALLOCATED");
        assertFalse(alloc.isDone());
        assertNull(alloc.getFixUrl());   // m1: FE chưa có giao diện phân bổ
    }

    @Test
    @DisplayName("F1: chỉ có tài sản không ACTIVE -> không tính là có tài sản, không tính là đã phân bổ")
    void evaluateReadiness_onlyNonActiveAssets_notCounted() {
        Vault v = vault(1);
        // Truy vấn chỉ lấy ACTIVE: tài sản REVOKED của vault không được trả về
        stubEstate(List.of(v), List.of(), List.of(v.getId()), List.of(), List.of(v.getId()));

        ReadinessResponse res = readinessService.evaluateReadiness(ownerId);

        assertFalse(item(res, "MASTER_VAULT_AND_ASSET").isDone());
        assertFalse(item(res, "ASSET_BENEFICIARY_ALLOCATED").isDone());
        assertTrue(item(res, "LEGAL_WILL_DOCUMENT").isDone());   // không có tài sản pháp lý, đã có 1 bản WILL
        assertFalse(item(res, "SAFETY_NET_CONFIGURED").isDone());
        assertEquals(55, res.getScore());
        verify(digitalAssetRepository).findByVaultIdInAndStatus(List.of(v.getId()), AssetStatus.ACTIVE);
        verify(assetAllocationRepository, never()).findAllocatedActiveAssetIdsByOwnerId(any());
        verify(assetAllocationRepository, never()).existsContingentForOwner(any());
    }

    @Test
    @DisplayName("F1: tài sản LEGAL_CLAIM_DOSSIER (N3) ở vault chưa có WILL -> tiêu chí di chúc chưa đạt")
    void evaluateReadiness_legalDossierAssetWithoutWill_notSatisfied() {
        Vault withWill = vault(1);
        Vault noWill = vault(2);
        DigitalAsset legal = asset(noWill, AssetType.OTHER);
        legal.setTransferMechanism(TransferMechanism.LEGAL_CLAIM_DOSSIER);
        DigitalAsset plain = asset(withWill, AssetType.SOCIAL_MEDIA);
        stubEstate(List.of(withWill, noWill), List.of(legal, plain), List.of(withWill.getId(), noWill.getId()),
                List.of(legal.getId(), plain.getId()), List.of(withWill.getId()));

        ReadinessResponse res = readinessService.evaluateReadiness(ownerId);

        ReadinessItemResponse will = item(res, "LEGAL_WILL_DOCUMENT");
        assertFalse(will.isDone());
        assertEquals("/owner/vaults/" + noWill.getId(), will.getFixUrl());
    }

    @Test
    @DisplayName("F1 M1: chưa có người thụ hưởng dự phòng -> tiêu chí 6 chưa đạt dù DMS/Backup Executor đã cấu hình")
    void evaluateReadiness_noContingent_safetyNetNotDone() {
        Vault v = vault(1);
        DigitalAsset a = asset(v, AssetType.EMAIL);
        stubEstate(List.of(v), List.of(a), List.of(v.getId()), List.of(a.getId()), List.of(v.getId()), false);

        ReadinessResponse res = readinessService.evaluateReadiness(ownerId);

        ReadinessItemResponse safety = item(res, "SAFETY_NET_CONFIGURED");
        assertFalse(safety.isDone());
        assertNull(safety.getFixUrl());
        assertEquals("items.safetyNetConfigured", safety.getTitleKey());
        assertEquals("Add a contingent beneficiary or DMS trusted confirmers", safety.getTitle());
        assertTrue(item(res, "PRIMARY_AND_BACKUP_EXECUTOR").isDone());
        assertEquals(90, res.getScore());
    }

    @Test
    @DisplayName("F1 M1: có người thụ hưởng dự phòng -> tiêu chí 6 đạt, không phụ thuộc Backup Executor / cấu hình DMS")
    void evaluateReadiness_contingent_safetyNetDone_withoutBackupOrDms() {
        Vault v = vault(1);
        DigitalAsset a = asset(v, AssetType.EMAIL);
        when(vaultRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId)).thenReturn(List.of(v));
        when(digitalAssetRepository.findByVaultIdInAndStatus(List.of(v.getId()), AssetStatus.ACTIVE)).thenReturn(List.of(a));
        when(vaultBeneficiaryRepository.findVaultIdsWithBeneficiaryByOwnerId(ownerId)).thenReturn(List.of(v.getId()));
        when(assetAllocationRepository.findAllocatedActiveAssetIdsByOwnerId(ownerId)).thenReturn(List.of(a.getId()));
        when(assetAllocationRepository.existsContingentForOwner(ownerId)).thenReturn(true);
        when(legalDocumentRepository.findVaultIdsWithDocumentTypeByOwnerId(ownerId, DocumentType.WILL)).thenReturn(List.of());
        when(executorAssignmentRepository.findAllByOwnerId(ownerId)).thenReturn(List.of(executor(ExecutorType.PRIMARY)));
        when(dmsConfigRepository.findByOwnerId(ownerId)).thenReturn(Optional.empty());

        ReadinessResponse res = readinessService.evaluateReadiness(ownerId);

        assertTrue(item(res, "SAFETY_NET_CONFIGURED").isDone());
        assertFalse(item(res, "PRIMARY_AND_BACKUP_EXECUTOR").isDone());   // thiếu Backup chỉ trừ ở tiêu chí 4
    }

    @Test
    @DisplayName("F1 m3: cơ chế đã lưu được ưu tiên; NULL mới suy ra từ loại tài sản")
    void isLegalDossierAsset_storedMechanismFirst_thenFallbackByType() {
        Vault v = vault(1);
        DigitalAsset bankStoredOther = asset(v, AssetType.BANK_ACCOUNT);
        bankStoredOther.setTransferMechanism(TransferMechanism.EXECUTOR_DISPOSITION);
        DigitalAsset otherStoredLegal = asset(v, AssetType.OTHER);
        otherStoredLegal.setTransferMechanism(TransferMechanism.LEGAL_CLAIM_DOSSIER);
        DigitalAsset bankLegacyNull = asset(v, AssetType.BANK_ACCOUNT);       // dữ liệu cũ: mechanism NULL
        DigitalAsset socialLegacyNull = asset(v, AssetType.SOCIAL_MEDIA);

        assertFalse(OwnerReadinessServiceImpl.isLegalDossierAsset(bankStoredOther));
        assertTrue(OwnerReadinessServiceImpl.isLegalDossierAsset(otherStoredLegal));
        assertTrue(OwnerReadinessServiceImpl.isLegalDossierAsset(bankLegacyNull));
        assertFalse(OwnerReadinessServiceImpl.isLegalDossierAsset(socialLegacyNull));
    }

    @Test
    @DisplayName("F1 m3: tài sản BANK_ACCOUNT nhưng lưu EXECUTOR_DISPOSITION thì không đòi Di chúc")
    void evaluateReadiness_storedNonLegalMechanism_doesNotRequireWill() {
        Vault withWill = vault(1);
        Vault noWill = vault(2);
        DigitalAsset bank = asset(noWill, AssetType.BANK_ACCOUNT);
        bank.setTransferMechanism(TransferMechanism.EXECUTOR_DISPOSITION);
        stubEstate(List.of(withWill, noWill), List.of(bank), List.of(withWill.getId(), noWill.getId()),
                List.of(bank.getId()), List.of(withWill.getId()));

        ReadinessResponse res = readinessService.evaluateReadiness(ownerId);

        assertTrue(item(res, "LEGAL_WILL_DOCUMENT").isDone());
    }

    @Test
    @DisplayName("F1 n3: ngưỡng hạng theo hằng số EXCELLENT_MIN / GOOD_MIN / FAIR_MIN")
    void gradeThresholds_areNamedConstants() {
        assertEquals(85, OwnerReadinessServiceImpl.EXCELLENT_MIN);
        assertEquals(65, OwnerReadinessServiceImpl.GOOD_MIN);
        assertEquals(40, OwnerReadinessServiceImpl.FAIR_MIN);
    }

    @Test
    @DisplayName("F1: nhiều vault vẫn chỉ gọi mỗi truy vấn theo lô 1 lần (không N+1)")
    void evaluateReadiness_manyVaults_batchedQueries() {
        Vault v1 = vault(1);
        Vault v2 = vault(2);
        Vault v3 = vault(3);
        DigitalAsset a1 = asset(v1, AssetType.BANK_ACCOUNT);
        DigitalAsset a2 = asset(v2, AssetType.DOCUMENT);
        DigitalAsset a3 = asset(v3, AssetType.EMAIL);
        stubEstate(List.of(v1, v2, v3), List.of(a1, a2, a3), List.of(v1.getId(), v2.getId(), v3.getId()),
                List.of(a1.getId(), a2.getId(), a3.getId()), List.of(v1.getId(), v2.getId()));

        readinessService.evaluateReadiness(ownerId);

        verify(digitalAssetRepository, times(1)).findByVaultIdInAndStatus(any(), any());
        verify(vaultBeneficiaryRepository, times(1)).findVaultIdsWithBeneficiaryByOwnerId(ownerId);
        verify(assetAllocationRepository, times(1)).findAllocatedActiveAssetIdsByOwnerId(ownerId);
        verify(assetAllocationRepository, times(1)).existsContingentForOwner(ownerId);
        verify(legalDocumentRepository, times(1)).findVaultIdsWithDocumentTypeByOwnerId(ownerId, DocumentType.WILL);
        verify(legalDocumentRepository, never()).findByVaultId(any());
    }
}
