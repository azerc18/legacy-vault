package com.ltld.app.legacyvault.service.readinessservice;

import com.ltld.app.legacyvault.dto.readinessdto.ReadinessResponse;
import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.entity.DmsConfig;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.LegalDocument;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.AssetStatus;
import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.DmsStatus;
import com.ltld.app.legacyvault.enums.DocumentType;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.ExecutorType;
import com.ltld.app.legacyvault.repository.DigitalAssetRepository;
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import com.ltld.app.legacyvault.repository.LegalDocumentRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OwnerReadinessServiceImplTest {

    @Mock private VaultRepository vaultRepository;
    @Mock private DigitalAssetRepository digitalAssetRepository;
    @Mock private LegalDocumentRepository legalDocumentRepository;
    @Mock private ExecutorAssignmentRepository executorAssignmentRepository;
    @Mock private DmsConfigRepository dmsConfigRepository;

    @InjectMocks
    private OwnerReadinessServiceImpl readinessService;

    private UUID ownerId;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
    }

    @Test
    @DisplayName("F1: Owner mới chưa cấu hình gì -> 0/100 điểm, hạng AT_RISK, đủ 7 tiêu chí kèm fixUrl")
    void evaluateReadiness_emptyEstate_returnsZeroAndAtRisk() {
        when(vaultRepository.findByOwnerIdWithBeneficiaryNewestFirst(ownerId)).thenReturn(List.of());
        when(executorAssignmentRepository.findAllByOwnerId(ownerId)).thenReturn(List.of());
        when(dmsConfigRepository.findByOwnerId(ownerId)).thenReturn(Optional.empty());

        ReadinessResponse res = readinessService.evaluateReadiness(ownerId);

        assertEquals(0, res.getScore());
        assertEquals(100, res.getMaxScore());
        assertEquals("AT_RISK", res.getGrade());
        assertEquals(0, res.getCompletedItems());
        assertEquals(7, res.getTotalItems());
        assertEquals(100, res.getItems().stream().mapToInt(i -> i.getPoints()).sum());
    }

    @Test
    @DisplayName("F1: Owner hoàn tất đầy đủ 7 tiêu chí -> 100/100 điểm, hạng EXCELLENT")
    void evaluateReadiness_fullyConfiguredEstate_returns100AndExcellent() {
        UUID vaultId = UUID.randomUUID();
        Vault vault = Vault.builder()
                .id(vaultId)
                .name("Master Estate Vault")
                .beneficiary(User.builder().id(UUID.randomUUID()).email("ben@example.com").build())
                .beneficiaryEmail("ben@example.com")
                .createdAt(LocalDateTime.now().minusDays(10))
                .updatedAt(LocalDateTime.now().minusDays(2))
                .build();

        DigitalAsset bankAsset = DigitalAsset.builder()
                .id(UUID.randomUUID())
                .vault(vault)
                .assetType(AssetType.BANK_ACCOUNT)
                .assetName("Vietcombank Savings")
                .status(AssetStatus.ACTIVE)
                .createdAt(LocalDateTime.now().minusDays(5))
                .build();

        LegalDocument willDoc = LegalDocument.builder()
                .id(UUID.randomUUID())
                .vault(vault)
                .documentType(DocumentType.WILL)
                .fileName("di-chuc-2026.pdf")
                .build();

        ExecutorAssignment primary = ExecutorAssignment.builder()
                .executorType(ExecutorType.PRIMARY)
                .status(ExecutorStatus.INACTIVE)
                .build();
        ExecutorAssignment backup = ExecutorAssignment.builder()
                .executorType(ExecutorType.BACKUP)
                .status(ExecutorStatus.INACTIVE)
                .build();

        DmsConfig dms = DmsConfig.builder()
                .status(DmsStatus.ACTIVE)
                .checkinIntervalDays(30)
                .gracePeriodDays(7)
                .reminderCount(3)
                .lastCheckinAt(LocalDateTime.now().minusDays(1))
                .build();

        when(vaultRepository.findByOwnerIdWithBeneficiaryNewestFirst(ownerId)).thenReturn(List.of(vault));
        when(digitalAssetRepository.findByVaultIdInAndStatus(List.of(vaultId), AssetStatus.ACTIVE))
                .thenReturn(List.of(bankAsset));
        when(legalDocumentRepository.findByVaultId(vaultId)).thenReturn(List.of(willDoc));
        when(executorAssignmentRepository.findAllByOwnerId(ownerId)).thenReturn(List.of(primary, backup));
        when(dmsConfigRepository.findByOwnerId(ownerId)).thenReturn(Optional.of(dms));

        ReadinessResponse res = readinessService.evaluateReadiness(ownerId);

        assertEquals(100, res.getScore());
        assertEquals("EXCELLENT", res.getGrade());
        assertEquals(7, res.getCompletedItems());
        assertTrue(res.getItems().stream().allMatch(i -> i.isDone()));
    }
}
