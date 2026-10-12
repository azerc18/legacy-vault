package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.dto.executordto.AssignedVaultResponse;
import com.ltld.app.legacyvault.dto.executordto.VaultHandoverProgressResponse;
import com.ltld.app.legacyvault.entity.BeneficiaryClaim;
import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.AssetStatus;
import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.ClaimStatus;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.ExecutorType;
import com.ltld.app.legacyvault.enums.HandoverStatus;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.enums.VerificationStatus;
import com.ltld.app.legacyvault.exception.ExecutorAssignmentNotFoundException;
import com.ltld.app.legacyvault.exception.ExecutorNotActiveException;
import com.ltld.app.legacyvault.repository.BeneficiaryClaimRepository;
import com.ltld.app.legacyvault.repository.DigitalAssetRepository;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import com.ltld.app.legacyvault.repository.IdentityVerificationRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExecutorHandoverServiceImplTest {

    @Mock private ExecutorAssignmentRepository executorAssignmentRepository;
    @Mock private VaultRepository vaultRepository;
    @Mock private DigitalAssetRepository digitalAssetRepository;
    @Mock private BeneficiaryClaimRepository beneficiaryClaimRepository;
    @Mock private IdentityVerificationRepository identityVerificationRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private ExecutorHandoverServiceImpl handoverService;

    private User owner;
    private User executor;
    private User beneficiary;
    private ExecutorAssignment activeAssignment;
    private UUID assignmentId;

    @BeforeEach
    void setUp() {
        owner = User.builder()
                .id(UUID.randomUUID())
                .email("owner@example.com")
                .fullName("Owner Test")
                .build();

        executor = User.builder()
                .id(UUID.randomUUID())
                .email("executor@example.com")
                .fullName("Executor Test")
                .build();

        beneficiary = User.builder()
                .id(UUID.randomUUID())
                .email("beneficiary@example.com")
                .fullName("Nguyen Van A")
                .build();

        assignmentId = UUID.randomUUID();
        activeAssignment = ExecutorAssignment.builder()
                .id(assignmentId)
                .owner(owner)
                .executorUser(executor)
                .executorType(ExecutorType.PRIMARY)
                .status(ExecutorStatus.ACTIVE)
                .activatedAt(LocalDateTime.now().minusDays(1))
                .acknowledgedAt(LocalDateTime.now().minusDays(1))
                .build();
    }

    @Test
    @DisplayName("Happy path: lấy tiến độ bàn giao thành công với đầy đủ trạng thái và thông tin masked")
    void getHandoverProgress_happyPath_returnsCorrectStatusesAndMaskedData() {
        UUID vault1Id = UUID.randomUUID();
        Vault vault1 = Vault.builder()
                .id(vault1Id)
                .owner(owner)
                .beneficiary(beneficiary)
                .name("Vault Active Pending")
                .description("Chờ xác minh")
                .status(VaultStatus.ACTIVE)
                .claimDeadlineAt(LocalDateTime.now().plusDays(30))
                .build();

        UUID vault2Id = UUID.randomUUID();
        Vault vault2 = Vault.builder()
                .id(vault2Id)
                .owner(owner)
                .beneficiary(beneficiary)
                .name("Vault Active Verified")
                .description("Đã xác minh")
                .status(VaultStatus.ACTIVE)
                .build();

        UUID vault3Id = UUID.randomUUID();
        Vault vault3 = Vault.builder()
                .id(vault3Id)
                .owner(owner)
                .beneficiary(beneficiary)
                .name("Vault Unlocked")
                .status(VaultStatus.UNLOCKED)
                .unlockedAt(LocalDateTime.now().minusHours(2))
                .build();

        UUID vault4Id = UUID.randomUUID();
        Vault vault4 = Vault.builder()
                .id(vault4Id)
                .owner(owner)
                .beneficiary(beneficiary)
                .name("Vault Claimed")
                .status(VaultStatus.CLAIMED)
                .build();

        UUID vault5Id = UUID.randomUUID();
        Vault vault5 = Vault.builder()
                .id(vault5Id)
                .owner(owner)
                .beneficiary(null)
                .name("Vault Archived Locked")
                .status(VaultStatus.ARCHIVED_LOCKED)
                .build();

        when(executorAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(activeAssignment));
        when(vaultRepository.findAllByOwnerId(owner.getId()))
                .thenReturn(List.of(vault1, vault2, vault3, vault4, vault5));

        when(digitalAssetRepository.countByVaultId(vault1Id)).thenReturn(3L);
        when(digitalAssetRepository.countByVaultId(vault2Id)).thenReturn(1L);
        when(digitalAssetRepository.countByVaultId(vault3Id)).thenReturn(5L);
        when(digitalAssetRepository.countByVaultId(vault4Id)).thenReturn(2L);
        when(digitalAssetRepository.countByVaultId(vault5Id)).thenReturn(0L);

        // Verification status
        when(identityVerificationRepository.existsByVaultIdAndStatus(vault1Id, VerificationStatus.SUCCESS)).thenReturn(false);
        when(identityVerificationRepository.existsByVaultIdAndStatus(vault2Id, VerificationStatus.SUCCESS)).thenReturn(true);
        when(identityVerificationRepository.existsByVaultIdAndStatus(vault3Id, VerificationStatus.SUCCESS)).thenReturn(true);
        when(identityVerificationRepository.existsByVaultIdAndStatus(vault4Id, VerificationStatus.SUCCESS)).thenReturn(true);
        when(identityVerificationRepository.existsByVaultIdAndStatus(vault5Id, VerificationStatus.SUCCESS)).thenReturn(false);

        List<VaultHandoverProgressResponse> responses =
                handoverService.getHandoverProgress(assignmentId, executor.getEmail());

        assertThat(responses).hasSize(5);

        // Vault 1: PENDING
        VaultHandoverProgressResponse r1 = responses.get(0);
        assertThat(r1.getVaultId()).isEqualTo(vault1Id);
        assertThat(r1.getHandoverStatus()).isEqualTo(HandoverStatus.PENDING);
        assertThat(r1.getAssetCount()).isEqualTo(3L);
        assertThat(r1.getBeneficiaryEmailMasked()).isEqualTo("b***@example.com");
        assertThat(r1.getBeneficiaryNameMasked()).isEqualTo("N*** V*** A***");

        // Vault 2: VERIFIED
        VaultHandoverProgressResponse r2 = responses.get(1);
        assertThat(r2.getVaultId()).isEqualTo(vault2Id);
        assertThat(r2.getHandoverStatus()).isEqualTo(HandoverStatus.VERIFIED);
        assertThat(r2.getAssetCount()).isEqualTo(1L);

        // Vault 3: UNLOCKED
        VaultHandoverProgressResponse r3 = responses.get(2);
        assertThat(r3.getVaultId()).isEqualTo(vault3Id);
        assertThat(r3.getHandoverStatus()).isEqualTo(HandoverStatus.UNLOCKED);
        assertThat(r3.getUnlockedAt()).isNotNull();

        // Vault 4: CLAIMED
        VaultHandoverProgressResponse r4 = responses.get(3);
        assertThat(r4.getVaultId()).isEqualTo(vault4Id);
        assertThat(r4.getHandoverStatus()).isEqualTo(HandoverStatus.CLAIMED);

        // Vault 5: ARCHIVED_LOCKED, beneficiary null
        VaultHandoverProgressResponse r5 = responses.get(4);
        assertThat(r5.getVaultId()).isEqualTo(vault5Id);
        assertThat(r5.getHandoverStatus()).isEqualTo(HandoverStatus.ARCHIVED_LOCKED);
        assertThat(r5.getBeneficiaryEmailMasked()).isNull();
        assertThat(r5.getBeneficiaryNameMasked()).isNull();
    }

    @Test
    @DisplayName("Edge case: claim record mang trạng thái CLAIMED thì handoverStatus trả về CLAIMED")
    void getHandoverProgress_claimRecordClaimed_returnsClaimedStatus() {
        UUID vaultId = UUID.randomUUID();
        Vault vault = Vault.builder()
                .id(vaultId)
                .owner(owner)
                .name("Vault With Claim")
                .status(VaultStatus.UNLOCKED)
                .build();

        BeneficiaryClaim claim = BeneficiaryClaim.builder()
                .id(UUID.randomUUID())
                .vault(vault)
                .status(ClaimStatus.CLAIMED)
                .claimDeadlineAt(LocalDateTime.now().plusDays(10))
                .build();

        when(executorAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(activeAssignment));
        when(vaultRepository.findAllByOwnerId(owner.getId())).thenReturn(List.of(vault));
        when(beneficiaryClaimRepository.findByVaultId(vaultId)).thenReturn(Optional.of(claim));

        List<VaultHandoverProgressResponse> responses =
                handoverService.getHandoverProgress(assignmentId, executor.getEmail());

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getHandoverStatus()).isEqualTo(HandoverStatus.CLAIMED);
        assertThat(responses.get(0).getClaimDeadlineAt()).isEqualTo(claim.getClaimDeadlineAt());
    }

    @Test
    @DisplayName("Edge case: không tìm thấy ExecutorAssignment -> ném ExecutorAssignmentNotFoundException (404)")
    void getHandoverProgress_assignmentNotFound_throwsException() {
        when(executorAssignmentRepository.findById(assignmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handoverService.getHandoverProgress(assignmentId, executor.getEmail()))
                .isInstanceOf(ExecutorAssignmentNotFoundException.class)
                .hasMessageContaining("Executor assignment not found.");
    }

    @Test
    @DisplayName("Edge case: ExecutorAssignment thuộc người khác -> ném ExecutorAssignmentNotFoundException (404)")
    void getHandoverProgress_differentUser_throwsNotFoundException() {
        when(executorAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(activeAssignment));

        assertThatThrownBy(() -> handoverService.getHandoverProgress(assignmentId, "other@example.com"))
                .isInstanceOf(ExecutorAssignmentNotFoundException.class)
                .hasMessageContaining("Executor assignment not found.");
    }

    @Test
    @DisplayName("Edge case: Executor chưa ACTIVE (INACTIVE) -> ném ExecutorNotActiveException (403)")
    void getHandoverProgress_executorInactive_throwsNotActiveException() {
        activeAssignment.setStatus(ExecutorStatus.INACTIVE);
        when(executorAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(activeAssignment));

        assertThatThrownBy(() -> handoverService.getHandoverProgress(assignmentId, executor.getEmail()))
                .isInstanceOf(ExecutorNotActiveException.class)
                .hasMessageContaining("Access denied. Executor has not been activated yet.");
    }

    @Test
    @DisplayName("Edge case: Executor đã bị REVOKED -> ném ExecutorNotActiveException (403)")
    void getHandoverProgress_executorRevoked_throwsNotActiveException() {
        activeAssignment.setStatus(ExecutorStatus.REVOKED);
        when(executorAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(activeAssignment));

        assertThatThrownBy(() -> handoverService.getHandoverProgress(assignmentId, executor.getEmail()))
                .isInstanceOf(ExecutorNotActiveException.class)
                .hasMessageContaining("Access denied. Executor has not been activated yet.");
    }

    @Test
    @DisplayName("Edge case: danh sách Vault rỗng -> trả về empty list")
    void getHandoverProgress_emptyVaults_returnsEmptyList() {
        when(executorAssignmentRepository.findById(assignmentId)).thenReturn(Optional.of(activeAssignment));
        when(vaultRepository.findAllByOwnerId(owner.getId())).thenReturn(Collections.emptyList());

        List<VaultHandoverProgressResponse> responses =
                handoverService.getHandoverProgress(assignmentId, executor.getEmail());

        assertThat(responses).isEmpty();
    }

    @Test
    @DisplayName("Gọi theo phiên đăng nhập (login): thành công khi có active assignment")
    void getHandoverProgress_byLogin_happyPath() {
        when(userRepository.findByEmail(executor.getEmail())).thenReturn(Optional.of(executor));
        when(executorAssignmentRepository.findAllByExecutorUserId(executor.getId()))
                .thenReturn(List.of(activeAssignment));
        when(vaultRepository.findAllByOwnerId(owner.getId())).thenReturn(Collections.emptyList());

        List<VaultHandoverProgressResponse> responses =
                handoverService.getHandoverProgress(executor.getEmail());

        assertThat(responses).isNotNull();
    }

    @Test
    @DisplayName("Gọi theo phiên đăng nhập (login): user không có assignment nào -> 404")
    void getHandoverProgress_byLogin_noAssignments_throwsNotFound() {
        when(userRepository.findByEmail(executor.getEmail())).thenReturn(Optional.of(executor));
        when(executorAssignmentRepository.findAllByExecutorUserId(executor.getId()))
                .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> handoverService.getHandoverProgress(executor.getEmail()))
                .isInstanceOf(ExecutorAssignmentNotFoundException.class);
    }

    @Test
    @DisplayName("Gọi theo phiên đăng nhập (login): user chỉ có assignment INACTIVE -> 403")
    void getHandoverProgress_byLogin_noActiveAssignment_throwsNotActive() {
        activeAssignment.setStatus(ExecutorStatus.INACTIVE);
        when(userRepository.findByEmail(executor.getEmail())).thenReturn(Optional.of(executor));
        when(executorAssignmentRepository.findAllByExecutorUserId(executor.getId()))
                .thenReturn(List.of(activeAssignment));

        assertThatThrownBy(() -> handoverService.getHandoverProgress(executor.getEmail()))
                .isInstanceOf(ExecutorNotActiveException.class);
    }

    // ===== FR-08: danh sách Vault được ủy quyền =====

    @Test
    @DisplayName("FR-08: trả về Vault của mọi Owner có assignment ACTIVE, giữ cả CLAIMED / ARCHIVED_LOCKED")
    void getAssignedVaults_multipleOwners_returnsAllVaultsWithStatus() {
        User owner2 = User.builder()
                .id(UUID.randomUUID())
                .email("owner2@example.com")
                .fullName("Owner Two")
                .build();
        ExecutorAssignment backupAssignment = ExecutorAssignment.builder()
                .id(UUID.randomUUID())
                .owner(owner2)
                .executorUser(executor)
                .executorType(ExecutorType.BACKUP)
                .status(ExecutorStatus.ACTIVE)
                .activatedAt(LocalDateTime.now().minusHours(3))
                .build();

        Vault claimedVault = Vault.builder()
                .id(UUID.randomUUID())
                .owner(owner)
                .beneficiary(beneficiary)
                .name("Vault Claimed")
                .status(VaultStatus.CLAIMED)
                .build();
        Vault archivedVault = Vault.builder()
                .id(UUID.randomUUID())
                .owner(owner2)
                .name("Vault Archived")
                .status(VaultStatus.ARCHIVED_LOCKED)
                .build();

        when(userRepository.findByEmail(executor.getEmail())).thenReturn(Optional.of(executor));
        when(executorAssignmentRepository.findAllByExecutorUserIdAndStatus(executor.getId(), ExecutorStatus.ACTIVE))
                .thenReturn(List.of(activeAssignment, backupAssignment));
        when(vaultRepository.findAllByOwnerIdIn(argThat(ids -> ids.containsAll(List.of(owner.getId(), owner2.getId())))))
                .thenReturn(List.of(claimedVault, archivedVault));
        when(digitalAssetRepository.findByVaultIdAndStatus(claimedVault.getId(), AssetStatus.ACTIVE))
                .thenReturn(List.of(
                        DigitalAsset.builder().assetType(AssetType.BANK_ACCOUNT).build(),
                        DigitalAsset.builder().assetType(AssetType.BANK_ACCOUNT).build(),
                        DigitalAsset.builder().assetType(AssetType.CRYPTO_WALLET).build()));
        when(digitalAssetRepository.findByVaultIdAndStatus(archivedVault.getId(), AssetStatus.ACTIVE))
                .thenReturn(Collections.emptyList());

        List<AssignedVaultResponse> responses = handoverService.getAssignedVaults(executor.getEmail());

        assertThat(responses).hasSize(2);

        AssignedVaultResponse claimed = responses.get(0);
        assertThat(claimed.getVaultId()).isEqualTo(claimedVault.getId());
        assertThat(claimed.getVaultStatus()).isEqualTo(VaultStatus.CLAIMED);
        assertThat(claimed.getOwnerId()).isEqualTo(owner.getId());
        assertThat(claimed.getOwnerName()).isEqualTo("Owner Test");
        assertThat(claimed.getAssetCount()).isEqualTo(3);
        assertThat(claimed.getAssetTypes()).containsExactly(AssetType.BANK_ACCOUNT, AssetType.CRYPTO_WALLET);
        assertThat(claimed.getBeneficiaryEmailMasked()).isEqualTo("b***@example.com");
        assertThat(claimed.getBeneficiaryNameMasked()).isEqualTo("N*** V*** A***");
        assertThat(claimed.getExecutorType()).isEqualTo(ExecutorType.PRIMARY);

        AssignedVaultResponse archived = responses.get(1);
        assertThat(archived.getVaultStatus()).isEqualTo(VaultStatus.ARCHIVED_LOCKED);
        assertThat(archived.getOwnerId()).isEqualTo(owner2.getId());
        assertThat(archived.getAssetCount()).isZero();
        assertThat(archived.getAssetTypes()).isEmpty();
        assertThat(archived.getBeneficiaryEmailMasked()).isNull();
        assertThat(archived.getExecutorType()).isEqualTo(ExecutorType.BACKUP);
        assertThat(archived.getActivatedAt()).isEqualTo(backupAssignment.getActivatedAt());
    }

    @Test
    @DisplayName("FR-08: Executor vừa PRIMARY vừa BACKUP của cùng Owner -> chỉ truy vấn Owner một lần")
    void getAssignedVaults_sameOwnerTwice_deduplicatesOwners() {
        ExecutorAssignment backupForSameOwner = ExecutorAssignment.builder()
                .id(UUID.randomUUID())
                .owner(owner)
                .executorUser(executor)
                .executorType(ExecutorType.BACKUP)
                .status(ExecutorStatus.ACTIVE)
                .build();

        when(userRepository.findByEmail(executor.getEmail())).thenReturn(Optional.of(executor));
        when(executorAssignmentRepository.findAllByExecutorUserIdAndStatus(executor.getId(), ExecutorStatus.ACTIVE))
                .thenReturn(List.of(activeAssignment, backupForSameOwner));
        when(vaultRepository.findAllByOwnerIdIn(any())).thenReturn(Collections.emptyList());

        handoverService.getAssignedVaults(executor.getEmail());

        verify(vaultRepository).findAllByOwnerIdIn(argThat(ids -> ids.size() == 1 && ids.contains(owner.getId())));
    }

    @Test
    @DisplayName("FR-08: login là user id (JWT subject) vẫn tìm được Executor")
    void getAssignedVaults_loginIsUserId_resolvesUser() {
        String login = executor.getId().toString();
        when(userRepository.findByEmail(login)).thenReturn(Optional.empty());
        when(userRepository.findById(executor.getId())).thenReturn(Optional.of(executor));
        when(executorAssignmentRepository.findAllByExecutorUserIdAndStatus(executor.getId(), ExecutorStatus.ACTIVE))
                .thenReturn(List.of(activeAssignment));
        when(vaultRepository.findAllByOwnerIdIn(any())).thenReturn(Collections.emptyList());

        assertThat(handoverService.getAssignedVaults(login)).isEmpty();
    }

    @Test
    @DisplayName("FR-08: không tìm thấy user -> 404")
    void getAssignedVaults_userNotFound_throwsNotFound() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handoverService.getAssignedVaults("ghost@example.com"))
                .isInstanceOf(ExecutorAssignmentNotFoundException.class);
    }

    @Test
    @DisplayName("FR-08: user chưa từng được phân công -> 404")
    void getAssignedVaults_noAssignments_throwsNotFound() {
        when(userRepository.findByEmail(executor.getEmail())).thenReturn(Optional.of(executor));
        when(executorAssignmentRepository.findAllByExecutorUserIdAndStatus(executor.getId(), ExecutorStatus.ACTIVE))
                .thenReturn(Collections.emptyList());
        when(executorAssignmentRepository.findAllByExecutorUserId(executor.getId()))
                .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> handoverService.getAssignedVaults(executor.getEmail()))
                .isInstanceOf(ExecutorAssignmentNotFoundException.class);
        verify(vaultRepository, never()).findAllByOwnerIdIn(any());
    }

    @Test
    @DisplayName("FR-08: chỉ có assignment INACTIVE / REVOKED -> 403")
    void getAssignedVaults_noActiveAssignment_throwsNotActive() {
        activeAssignment.setStatus(ExecutorStatus.REVOKED);
        when(userRepository.findByEmail(executor.getEmail())).thenReturn(Optional.of(executor));
        when(executorAssignmentRepository.findAllByExecutorUserIdAndStatus(executor.getId(), ExecutorStatus.ACTIVE))
                .thenReturn(Collections.emptyList());
        when(executorAssignmentRepository.findAllByExecutorUserId(executor.getId()))
                .thenReturn(List.of(activeAssignment));

        assertThatThrownBy(() -> handoverService.getAssignedVaults(executor.getEmail()))
                .isInstanceOf(ExecutorNotActiveException.class);
        verify(vaultRepository, never()).findAllByOwnerIdIn(any());
    }
}
