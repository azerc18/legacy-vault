package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.dto.executordto.ExecutorAssignedVaultsResponse;
import com.ltld.app.legacyvault.dto.executordto.ExecutorVaultAssetResponse;
import com.ltld.app.legacyvault.dto.executordto.ExecutorVaultBeneficiaryResponse;
import com.ltld.app.legacyvault.dto.executordto.ExecutorVaultResponse;
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
import com.ltld.app.legacyvault.repository.VaultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecutorAssetServiceImplTest {

    private static final String LOGIN = "executor@example.com";

    @Mock private ExecutorAccessGuard executorAccessGuard;
    @Mock private VaultRepository vaultRepository;
    @Mock private DigitalAssetRepository digitalAssetRepository;
    @InjectMocks private ExecutorAssetServiceImpl executorAssetService;

    private UUID assignmentId;
    private UUID ownerId;
    private User owner;
    private ExecutorAssignment assignment;

    @BeforeEach
    void setUp() {
        assignmentId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        owner = User.builder().id(ownerId).fullName("Nguyen Van Owner").build();
        User executor = User.builder().id(UUID.randomUUID()).email(LOGIN).build();
        assignment = ExecutorAssignment.builder()
                .id(assignmentId)
                .owner(owner)
                .executorUser(executor)
                .status(ExecutorStatus.ACTIVE)
                .build();
    }

    private void stubGuardAllows() {
        when(executorAccessGuard.requireActiveAssignment(assignmentId, LOGIN)).thenReturn(assignment);
    }

    @Test
    @DisplayName("Happy path: 2 vaults (1 with beneficiary, 1 without), active assets grouped per vault")
    void getAssignedVaults_happyPath_returnsCorrectDto() {
        stubGuardAllows();

        UUID benId = UUID.randomUUID();
        User beneficiary = User.builder().id(benId).fullName("Le Thi Beneficiary").email("ben@secret.com").build();

        Vault vault1 = Vault.builder().id(UUID.randomUUID()).owner(owner).name("Vault Alpha")
                .status(VaultStatus.ACTIVE).description("Handover instructions for Alpha")
                .beneficiary(beneficiary).build();
        Vault vault2 = Vault.builder().id(UUID.randomUUID()).owner(owner).name("Vault Beta")
                .status(VaultStatus.ARCHIVED_LOCKED).description(null).beneficiary(null).build();
        when(vaultRepository.findByOwnerIdWithBeneficiary(ownerId)).thenReturn(List.of(vault1, vault2));

        DigitalAsset asset1 = DigitalAsset.builder().id(UUID.randomUUID()).vault(vault1)
                .assetType(AssetType.CRYPTO_WALLET).assetName("BTC Wallet").status(AssetStatus.ACTIVE).build();
        DigitalAsset asset2 = DigitalAsset.builder().id(UUID.randomUUID()).vault(vault1)
                .assetType(AssetType.BANK_ACCOUNT).assetName("Techcombank").status(AssetStatus.ACTIVE).build();
        when(digitalAssetRepository.findByVaultIdInAndStatus(any(), eq(AssetStatus.ACTIVE)))
                .thenReturn(List.of(asset1, asset2));

        ExecutorAssignedVaultsResponse response = executorAssetService.getAssignedVaults(assignmentId, LOGIN);

        assertEquals(assignmentId, response.getAssignmentId());
        assertEquals("Nguyen Van Owner", response.getOwnerFullName());
        assertEquals(2, response.getVaults().size());

        ExecutorVaultResponse v1 = response.getVaults().get(0);
        assertEquals(vault1.getId(), v1.getVaultId());
        assertEquals("Vault Alpha", v1.getName());
        assertEquals(VaultStatus.ACTIVE, v1.getStatus());
        assertEquals("Handover instructions for Alpha", v1.getHandoverInstructions());
        assertEquals(benId, v1.getBeneficiary().getId());
        assertEquals("Le Thi Beneficiary", v1.getBeneficiary().getFullName());
        assertEquals(2, v1.getAssets().size());

        ExecutorVaultResponse v2 = response.getVaults().get(1);
        assertEquals(VaultStatus.ARCHIVED_LOCKED, v2.getStatus());
        assertNull(v2.getHandoverInstructions());
        assertNull(v2.getBeneficiary());
        assertTrue(v2.getAssets().isEmpty());
    }

    @Test
    @DisplayName("Guard rejects with 404 -> no vault or asset query is made")
    void getAssignedVaults_guardNotFound_propagatesAndSkipsQueries() {
        when(executorAccessGuard.requireActiveAssignment(assignmentId, LOGIN))
                .thenThrow(new ExecutorAssignmentNotFoundException("Executor assignment not found."));

        assertThrows(ExecutorAssignmentNotFoundException.class,
                () -> executorAssetService.getAssignedVaults(assignmentId, LOGIN));

        verifyNoInteractions(vaultRepository);
        verifyNoInteractions(digitalAssetRepository);
    }

    @Test
    @DisplayName("Guard rejects with 403 -> no vault or asset query is made")
    void getAssignedVaults_guardNotActive_propagatesAndSkipsQueries() {
        when(executorAccessGuard.requireActiveAssignment(assignmentId, LOGIN))
                .thenThrow(new ExecutorNotActiveException("Access is not available yet"));

        assertThrows(ExecutorNotActiveException.class,
                () -> executorAssetService.getAssignedVaults(assignmentId, LOGIN));

        verifyNoInteractions(vaultRepository);
        verifyNoInteractions(digitalAssetRepository);
    }

    @Test
    @DisplayName("Owner has 0 vaults -> empty list and the asset repository is not called")
    void getAssignedVaults_ownerHasNoVaults_returnsEmptyListWithoutQueryingAssets() {
        stubGuardAllows();
        when(vaultRepository.findByOwnerIdWithBeneficiary(ownerId)).thenReturn(Collections.emptyList());

        ExecutorAssignedVaultsResponse response = executorAssetService.getAssignedVaults(assignmentId, LOGIN);

        assertEquals(assignmentId, response.getAssignmentId());
        assertEquals("Nguyen Van Owner", response.getOwnerFullName());
        assertNotNull(response.getVaults());
        assertTrue(response.getVaults().isEmpty());
        verifyNoInteractions(digitalAssetRepository);
    }

    @Test
    @DisplayName("Vault without assets -> assets is an empty list, not null")
    void getAssignedVaults_vaultHasZeroAssets_assetsListIsEmpty() {
        stubGuardAllows();
        Vault vault = Vault.builder().id(UUID.randomUUID()).owner(owner).name("Vault Empty")
                .status(VaultStatus.ACTIVE).build();
        when(vaultRepository.findByOwnerIdWithBeneficiary(ownerId)).thenReturn(List.of(vault));
        when(digitalAssetRepository.findByVaultIdInAndStatus(any(), eq(AssetStatus.ACTIVE)))
                .thenReturn(Collections.emptyList());

        ExecutorAssignedVaultsResponse response = executorAssetService.getAssignedVaults(assignmentId, LOGIN);

        assertEquals(1, response.getVaults().size());
        assertNotNull(response.getVaults().get(0).getAssets());
        assertTrue(response.getVaults().get(0).getAssets().isEmpty());
    }

    @Test
    @DisplayName("Assets are queried by the owner's vault ids and only with ACTIVE status")
    void getAssignedVaults_queriesOnlyActiveAssets() {
        stubGuardAllows();
        Vault vault = Vault.builder().id(UUID.randomUUID()).owner(owner).name("Vault One")
                .status(VaultStatus.ACTIVE).build();
        when(vaultRepository.findByOwnerIdWithBeneficiary(ownerId)).thenReturn(List.of(vault));
        when(digitalAssetRepository.findByVaultIdInAndStatus(any(), eq(AssetStatus.ACTIVE)))
                .thenReturn(Collections.emptyList());

        executorAssetService.getAssignedVaults(assignmentId, LOGIN);

        verify(digitalAssetRepository).findByVaultIdInAndStatus(List.of(vault.getId()), AssetStatus.ACTIVE);
    }

    @Test
    @DisplayName("Vaults are loaded only for the assignment's owner")
    void getAssignedVaults_loadsVaultsOfAssignmentOwnerOnly() {
        stubGuardAllows();
        when(vaultRepository.findByOwnerIdWithBeneficiary(ownerId)).thenReturn(Collections.emptyList());

        executorAssetService.getAssignedVaults(assignmentId, LOGIN);

        verify(vaultRepository).findByOwnerIdWithBeneficiary(ownerId);
    }

    @Test
    @DisplayName("Invited by email but no account yet -> beneficiaryPending is true and beneficiary is null")
    void getAssignedVaults_beneficiaryInvitedByEmail_isPending() {
        stubGuardAllows();
        Vault vault = Vault.builder().id(UUID.randomUUID()).owner(owner).name("Vault Pending")
                .status(VaultStatus.ACTIVE).beneficiaryEmail("invitee@example.com").build();
        when(vaultRepository.findByOwnerIdWithBeneficiary(ownerId)).thenReturn(List.of(vault));
        when(digitalAssetRepository.findByVaultIdInAndStatus(any(), eq(AssetStatus.ACTIVE)))
                .thenReturn(Collections.emptyList());

        ExecutorVaultResponse res = executorAssetService.getAssignedVaults(assignmentId, LOGIN).getVaults().get(0);

        assertTrue(res.isBeneficiaryPending());
        assertNull(res.getBeneficiary());
    }

    @Test
    @DisplayName("Beneficiary already linked -> beneficiaryPending is false even if an email is still stored")
    void getAssignedVaults_beneficiaryLinked_isNotPending() {
        stubGuardAllows();
        User linked = User.builder().id(UUID.randomUUID()).fullName("Linked Person").build();
        Vault vault = Vault.builder().id(UUID.randomUUID()).owner(owner).name("Vault Linked")
                .status(VaultStatus.ACTIVE).beneficiary(linked).beneficiaryEmail("linked@example.com").build();
        when(vaultRepository.findByOwnerIdWithBeneficiary(ownerId)).thenReturn(List.of(vault));
        when(digitalAssetRepository.findByVaultIdInAndStatus(any(), eq(AssetStatus.ACTIVE)))
                .thenReturn(Collections.emptyList());

        ExecutorVaultResponse res = executorAssetService.getAssignedVaults(assignmentId, LOGIN).getVaults().get(0);

        assertFalse(res.isBeneficiaryPending());
        assertEquals("Linked Person", res.getBeneficiary().getFullName());
    }

    @Test
    @DisplayName("No beneficiary and no invited email (or a blank one) -> beneficiaryPending is false")
    void getAssignedVaults_noBeneficiaryAtAll_isNotPending() {
        stubGuardAllows();
        Vault none = Vault.builder().id(UUID.randomUUID()).owner(owner).name("Vault None")
                .status(VaultStatus.ACTIVE).build();
        Vault blank = Vault.builder().id(UUID.randomUUID()).owner(owner).name("Vault Blank")
                .status(VaultStatus.ACTIVE).beneficiaryEmail("   ").build();
        when(vaultRepository.findByOwnerIdWithBeneficiary(ownerId)).thenReturn(List.of(none, blank));
        when(digitalAssetRepository.findByVaultIdInAndStatus(any(), eq(AssetStatus.ACTIVE)))
                .thenReturn(Collections.emptyList());

        List<ExecutorVaultResponse> vaults = executorAssetService.getAssignedVaults(assignmentId, LOGIN).getVaults();

        assertFalse(vaults.get(0).isBeneficiaryPending());
        assertFalse(vaults.get(1).isBeneficiaryPending());
    }

    @Test
    @DisplayName("Null handover instructions -> field stays null without NPE")
    void getAssignedVaults_handoverInstructionsNull_noNpe() {
        stubGuardAllows();
        Vault vault = Vault.builder().id(UUID.randomUUID()).owner(owner).name("Vault Null Desc")
                .description(null).status(VaultStatus.ACTIVE).build();
        when(vaultRepository.findByOwnerIdWithBeneficiary(ownerId)).thenReturn(List.of(vault));
        when(digitalAssetRepository.findByVaultIdInAndStatus(any(), eq(AssetStatus.ACTIVE)))
                .thenReturn(Collections.emptyList());

        ExecutorAssignedVaultsResponse res = executorAssetService.getAssignedVaults(assignmentId, LOGIN);

        assertNull(res.getVaults().get(0).getHandoverInstructions());
    }

    @Test
    @DisplayName("DTO classes must not declare any secret-bearing field")
    void dtoClasses_mustNotContainSensitiveFields() {
        List<Class<?>> dtos = List.of(
                ExecutorAssignedVaultsResponse.class,
                ExecutorVaultResponse.class,
                ExecutorVaultBeneficiaryResponse.class,
                ExecutorVaultAssetResponse.class
        );
        Set<String> forbiddenFieldNames = Set.of(
                "encryptedsecret", "notesencrypted", "encryptionkeyref",
                "attachmenturl", "passwordhash", "email"
        );

        for (Class<?> clazz : dtos) {
            for (Field field : clazz.getDeclaredFields()) {
                assertFalse(forbiddenFieldNames.contains(field.getName().toLowerCase(Locale.ROOT)),
                        "DTO " + clazz.getSimpleName() + " must not contain sensitive field: " + field.getName());
            }
        }
    }
}
