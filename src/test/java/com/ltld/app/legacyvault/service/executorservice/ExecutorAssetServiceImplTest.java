package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.dto.executordto.*;
import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.AssetStatus;
import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.exception.ExecutorAssignmentNotFoundException;
import com.ltld.app.legacyvault.exception.ExecutorNotActiveException;
import com.ltld.app.legacyvault.repository.DigitalAssetRepository;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExecutorAssetServiceImplTest {

    @Mock
    private ExecutorAssignmentRepository executorAssignmentRepository;

    @Mock
    private VaultRepository vaultRepository;

    @Mock
    private DigitalAssetRepository digitalAssetRepository;

    @InjectMocks
    private ExecutorAssetServiceImpl executorAssetService;

    @Test
    @DisplayName("1. Happy path: ACTIVE, 2 vaults (1 with beneficiary, 1 without), assets grouped correctly")
    void getAssignedVaults_happyPath_returnsCorrectDto() {
        UUID assignmentId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID executorId = UUID.randomUUID();
        String executorEmail = "executor@example.com";

        User owner = User.builder().id(ownerId).fullName("Nguyen Van Owner").build();
        User executor = User.builder().id(executorId).email(executorEmail).fullName("Tran Van Executor").build();

        ExecutorAssignment assignment = ExecutorAssignment.builder()
                .id(assignmentId)
                .owner(owner)
                .executorUser(executor)
                .status(ExecutorStatus.ACTIVE)
                .build();

        when(executorAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));

        UUID benId = UUID.randomUUID();
        User beneficiary = User.builder().id(benId).fullName("Le Thi Beneficiary").email("ben@secret.com").build();

        UUID vault1Id = UUID.randomUUID();
        Vault vault1 = Vault.builder()
                .id(vault1Id)
                .owner(owner)
                .name("Vault Alpha")
                .status(VaultStatus.ACTIVE)
                .description("Handover instructions for Alpha")
                .beneficiary(beneficiary)
                .build();

        UUID vault2Id = UUID.randomUUID();
        Vault vault2 = Vault.builder()
                .id(vault2Id)
                .owner(owner)
                .name("Vault Beta")
                .status(VaultStatus.ARCHIVED_LOCKED)
                .description(null)
                .beneficiary(null)
                .build();

        when(vaultRepository.findByOwnerIdWithBeneficiary(ownerId)).thenReturn(List.of(vault1, vault2));

        DigitalAsset asset1 = DigitalAsset.builder()
                .id(UUID.randomUUID())
                .vault(vault1)
                .assetType(AssetType.CRYPTO_WALLET)
                .assetName("BTC Wallet")
                .status(AssetStatus.ACTIVE)
                .build();

        DigitalAsset asset2 = DigitalAsset.builder()
                .id(UUID.randomUUID())
                .vault(vault1)
                .assetType(AssetType.BANK_ACCOUNT)
                .assetName("Techcombank")
                .status(AssetStatus.ACTIVE)
                .build();

        when(digitalAssetRepository.findByVaultIdInAndStatus(any(), eq(AssetStatus.ACTIVE)))
                .thenReturn(List.of(asset1, asset2));

        ExecutorAssignedVaultsResponse response = executorAssetService.getAssignedVaults(assignmentId, executorEmail);

        assertNotNull(response);
        assertEquals(assignmentId, response.getAssignmentId());
        assertEquals("Nguyen Van Owner", response.getOwnerFullName());
        assertEquals(2, response.getVaults().size());

        ExecutorVaultResponse v1 = response.getVaults().get(0);
        assertEquals(vault1Id, v1.getVaultId());
        assertEquals("Vault Alpha", v1.getName());
        assertEquals(VaultStatus.ACTIVE, v1.getStatus());
        assertEquals("Handover instructions for Alpha", v1.getHandoverInstructions());
        assertNotNull(v1.getBeneficiary());
        assertEquals(benId, v1.getBeneficiary().getId());
        assertEquals("Le Thi Beneficiary", v1.getBeneficiary().getFullName());
        assertEquals(2, v1.getAssets().size());

        ExecutorVaultResponse v2 = response.getVaults().get(1);
        assertEquals(vault2Id, v2.getVaultId());
        assertEquals("Vault Beta", v2.getName());
        assertEquals(VaultStatus.ARCHIVED_LOCKED, v2.getStatus());
        assertNull(v2.getHandoverInstructions());
        assertNull(v2.getBeneficiary());
        assertTrue(v2.getAssets().isEmpty());
    }

    @Test
    @DisplayName("2. Assignment does not exist -> throws ExecutorAssignmentNotFoundException (404)")
    void getAssignedVaults_assignmentNotFound_throws404() {
        UUID assignmentId = UUID.randomUUID();
        when(executorAssignmentRepository.findById(assignmentId)).thenReturn(Optional.empty());

        assertThrows(ExecutorAssignmentNotFoundException.class,
                () -> executorAssetService.getAssignedVaults(assignmentId, "executor@example.com"));

        verifyNoInteractions(vaultRepository);
        verifyNoInteractions(digitalAssetRepository);
    }

    @Test
    @DisplayName("3. IDOR: Assignment belongs to another executor -> throws 404 and does not call vaultRepository")
    void getAssignedVaults_callerMismatch_throws404() {
        UUID assignmentId = UUID.randomUUID();
        User realExecutor = User.builder().id(UUID.randomUUID()).email("real@example.com").build();
        ExecutorAssignment assignment = ExecutorAssignment.builder()
                .id(assignmentId)
                .executorUser(realExecutor)
                .status(ExecutorStatus.ACTIVE)
                .build();

        when(executorAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));

        assertThrows(ExecutorAssignmentNotFoundException.class,
                () -> executorAssetService.getAssignedVaults(assignmentId, "attacker@example.com"));

        verifyNoInteractions(vaultRepository);
        verifyNoInteractions(digitalAssetRepository);
    }

    @Test
    @DisplayName("4. Assignment INACTIVE -> throws ExecutorNotActiveException (403) and does not call vaultRepository")
    void getAssignedVaults_assignmentInactive_throws403() {
        UUID assignmentId = UUID.randomUUID();
        User executor = User.builder().id(UUID.randomUUID()).email("executor@example.com").build();
        ExecutorAssignment assignment = ExecutorAssignment.builder()
                .id(assignmentId)
                .executorUser(executor)
                .status(ExecutorStatus.INACTIVE)
                .build();

        when(executorAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));

        ExecutorNotActiveException ex = assertThrows(ExecutorNotActiveException.class,
                () -> executorAssetService.getAssignedVaults(assignmentId, "executor@example.com"));
        assertEquals("Chưa đến thời điểm thực hiện quyền", ex.getMessage());

        verifyNoInteractions(vaultRepository);
        verifyNoInteractions(digitalAssetRepository);
    }

    @Test
    @DisplayName("5. Assignment REVOKED -> throws ExecutorNotActiveException (403)")
    void getAssignedVaults_assignmentRevoked_throws403() {
        UUID assignmentId = UUID.randomUUID();
        User executor = User.builder().id(UUID.randomUUID()).email("executor@example.com").build();
        ExecutorAssignment assignment = ExecutorAssignment.builder()
                .id(assignmentId)
                .executorUser(executor)
                .status(ExecutorStatus.REVOKED)
                .build();

        when(executorAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));

        assertThrows(ExecutorNotActiveException.class,
                () -> executorAssetService.getAssignedVaults(assignmentId, "executor@example.com"));

        verifyNoInteractions(vaultRepository);
        verifyNoInteractions(digitalAssetRepository);
    }

    @Test
    @DisplayName("6. Owner has 0 vaults -> returns empty list and digitalAssetRepository is NOT called")
    void getAssignedVaults_ownerHasNoVaults_returnsEmptyListWithoutQueryingAssets() {
        UUID assignmentId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        User owner = User.builder().id(ownerId).fullName("Owner Zero").build();
        User executor = User.builder().id(UUID.randomUUID()).email("executor@example.com").build();

        ExecutorAssignment assignment = ExecutorAssignment.builder()
                .id(assignmentId)
                .owner(owner)
                .executorUser(executor)
                .status(ExecutorStatus.ACTIVE)
                .build();

        when(executorAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(vaultRepository.findByOwnerIdWithBeneficiary(ownerId)).thenReturn(Collections.emptyList());

        ExecutorAssignedVaultsResponse response = executorAssetService.getAssignedVaults(assignmentId, "executor@example.com");

        assertNotNull(response);
        assertEquals(assignmentId, response.getAssignmentId());
        assertEquals("Owner Zero", response.getOwnerFullName());
        assertNotNull(response.getVaults());
        assertTrue(response.getVaults().isEmpty());

        verifyNoInteractions(digitalAssetRepository);
    }

    @Test
    @DisplayName("7. Vault has 0 assets -> assets list is empty (not null)")
    void getAssignedVaults_vaultHasZeroAssets_assetsListIsEmpty() {
        UUID assignmentId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        User owner = User.builder().id(ownerId).fullName("Owner One").build();
        User executor = User.builder().id(UUID.randomUUID()).email("executor@example.com").build();

        ExecutorAssignment assignment = ExecutorAssignment.builder()
                .id(assignmentId)
                .owner(owner)
                .executorUser(executor)
                .status(ExecutorStatus.ACTIVE)
                .build();

        UUID vaultId = UUID.randomUUID();
        Vault vault = Vault.builder().id(vaultId).owner(owner).name("Vault Empty").status(VaultStatus.ACTIVE).build();

        when(executorAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(vaultRepository.findByOwnerIdWithBeneficiary(ownerId)).thenReturn(List.of(vault));
        when(digitalAssetRepository.findByVaultIdInAndStatus(any(), eq(AssetStatus.ACTIVE))).thenReturn(Collections.emptyList());

        ExecutorAssignedVaultsResponse response = executorAssetService.getAssignedVaults(assignmentId, "executor@example.com");

        assertNotNull(response);
        assertEquals(1, response.getVaults().size());
        assertNotNull(response.getVaults().get(0).getAssets());
        assertTrue(response.getVaults().get(0).getAssets().isEmpty());
    }

    @Test
    @DisplayName("8. Verifies status ACTIVE is passed when querying digital assets")
    void getAssignedVaults_queriesOnlyActiveAssets() {
        UUID assignmentId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        User owner = User.builder().id(ownerId).fullName("Owner Two").build();
        User executor = User.builder().id(UUID.randomUUID()).email("executor@example.com").build();

        ExecutorAssignment assignment = ExecutorAssignment.builder()
                .id(assignmentId)
                .owner(owner)
                .executorUser(executor)
                .status(ExecutorStatus.ACTIVE)
                .build();

        UUID vaultId = UUID.randomUUID();
        Vault vault = Vault.builder().id(vaultId).owner(owner).name("Vault One").status(VaultStatus.ACTIVE).build();

        when(executorAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(vaultRepository.findByOwnerIdWithBeneficiary(ownerId)).thenReturn(List.of(vault));
        when(digitalAssetRepository.findByVaultIdInAndStatus(any(), eq(AssetStatus.ACTIVE))).thenReturn(Collections.emptyList());

        executorAssetService.getAssignedVaults(assignmentId, "executor@example.com");

        verify(digitalAssetRepository).findByVaultIdInAndStatus(List.of(vaultId), AssetStatus.ACTIVE);
    }

    @Test
    @DisplayName("9. Login with uppercase email and UUID string are both accepted")
    void getAssignedVaults_acceptsCaseInsensitiveEmailAndUserId() {
        UUID assignmentId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID executorId = UUID.randomUUID();
        User owner = User.builder().id(ownerId).fullName("Owner Case").build();
        User executor = User.builder().id(executorId).email("executor@example.com").build();

        ExecutorAssignment assignment = ExecutorAssignment.builder()
                .id(assignmentId)
                .owner(owner)
                .executorUser(executor)
                .status(ExecutorStatus.ACTIVE)
                .build();

        when(executorAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(vaultRepository.findByOwnerIdWithBeneficiary(ownerId)).thenReturn(Collections.emptyList());

        // Case 1: Uppercase email
        ExecutorAssignedVaultsResponse res1 = executorAssetService.getAssignedVaults(assignmentId, "EXECUTOR@EXAMPLE.COM");
        assertNotNull(res1);

        // Case 2: User UUID string
        ExecutorAssignedVaultsResponse res2 = executorAssetService.getAssignedVaults(assignmentId, executorId.toString());
        assertNotNull(res2);
    }

    @Test
    @DisplayName("10. Null login -> throws ExecutorAssignmentNotFoundException (404)")
    void getAssignedVaults_nullLogin_throws404() {
        UUID assignmentId = UUID.randomUUID();
        User executor = User.builder().id(UUID.randomUUID()).email("executor@example.com").build();
        ExecutorAssignment assignment = ExecutorAssignment.builder()
                .id(assignmentId)
                .executorUser(executor)
                .status(ExecutorStatus.ACTIVE)
                .build();

        when(executorAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));

        assertThrows(ExecutorAssignmentNotFoundException.class,
                () -> executorAssetService.getAssignedVaults(assignmentId, null));
    }

    @Test
    @DisplayName("11. Handover instructions null -> field is null, no NPE")
    void getAssignedVaults_handoverInstructionsNull_noNpe() {
        UUID assignmentId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        User owner = User.builder().id(ownerId).fullName("Owner Instruction").build();
        User executor = User.builder().id(UUID.randomUUID()).email("executor@example.com").build();

        ExecutorAssignment assignment = ExecutorAssignment.builder()
                .id(assignmentId)
                .owner(owner)
                .executorUser(executor)
                .status(ExecutorStatus.ACTIVE)
                .build();

        UUID vaultId = UUID.randomUUID();
        Vault vault = Vault.builder()
                .id(vaultId)
                .owner(owner)
                .name("Vault Null Desc")
                .description(null)
                .status(VaultStatus.ACTIVE)
                .build();

        when(executorAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(vaultRepository.findByOwnerIdWithBeneficiary(ownerId)).thenReturn(List.of(vault));
        when(digitalAssetRepository.findByVaultIdInAndStatus(any(), eq(AssetStatus.ACTIVE))).thenReturn(Collections.emptyList());

        ExecutorAssignedVaultsResponse res = executorAssetService.getAssignedVaults(assignmentId, "executor@example.com");

        assertNotNull(res);
        assertNull(res.getVaults().get(0).getHandoverInstructions());
    }

    @Test
    @DisplayName("12. Reflection check: DTO classes must not contain sensitive secret fields")
    void dtoClasses_mustNotContainSensitiveFields() {
        List<Class<?>> dtos = List.of(
                ExecutorAssignedVaultsResponse.class,
                ExecutorVaultResponse.class,
                ExecutorVaultBeneficiaryResponse.class,
                ExecutorVaultAssetResponse.class
        );

        Set<String> forbiddenFieldNames = Set.of(
                "encryptedsecret",
                "notesencrypted",
                "encryptionkeyref",
                "attachmenturl",
                "passwordhash",
                "email"
        );

        for (Class<?> clazz : dtos) {
            for (Field field : clazz.getDeclaredFields()) {
                String fieldLower = field.getName().toLowerCase(Locale.ROOT);
                assertFalse(forbiddenFieldNames.contains(fieldLower),
                        "DTO " + clazz.getSimpleName() + " must not contain sensitive field: " + field.getName());
            }
        }
    }
}
