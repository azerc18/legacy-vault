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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Triển khai nghiệp vụ FR-10: Theo dõi tiến độ bàn giao từng tài sản / Vault,
 * và FR-08: Xem danh sách Vault được ủy quyền quản lý.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExecutorHandoverServiceImpl implements ExecutorHandoverService {

    private final ExecutorAssignmentRepository executorAssignmentRepository;
    private final VaultRepository vaultRepository;
    private final DigitalAssetRepository digitalAssetRepository;
    private final BeneficiaryClaimRepository beneficiaryClaimRepository;
    private final IdentityVerificationRepository identityVerificationRepository;
    private final UserRepository userRepository;

    @Override
    public List<VaultHandoverProgressResponse> getHandoverProgress(UUID assignmentId, String executorLogin) {
        ExecutorAssignment assignment = executorAssignmentRepository.findById(assignmentId)
                .filter(a -> isSameUser(a.getExecutorUser(), executorLogin))
                .orElseThrow(() -> new ExecutorAssignmentNotFoundException("Executor assignment not found."));

        validateExecutorActive(assignment);

        return fetchVaultProgress(assignment.getOwner().getId());
    }

    @Override
    public List<VaultHandoverProgressResponse> getHandoverProgress(String executorLogin) {
        User user = findUserByLogin(executorLogin)
                .orElseThrow(() -> new ExecutorAssignmentNotFoundException("Executor assignment not found."));

        List<ExecutorAssignment> assignments = executorAssignmentRepository.findAllByExecutorUserId(user.getId());
        if (assignments.isEmpty()) {
            throw new ExecutorAssignmentNotFoundException("Executor assignment not found.");
        }

        ExecutorAssignment activeAssignment = assignments.stream()
                .filter(a -> a.getStatus() == ExecutorStatus.ACTIVE)
                .findFirst()
                .orElseThrow(() -> new ExecutorNotActiveException("Access denied. Executor has not been activated yet."));

        // Reuse helper directly to avoid redundant findById + isSameUser DB calls
        return fetchVaultProgress(activeAssignment.getOwner().getId());
    }

    // FR-08: Executor xem danh sách Vault thuộc mọi Owner đã phân công mình (assignment ACTIVE)
    @Override
    public List<AssignedVaultResponse> getAssignedVaults(String executorLogin) {
        User user = findUserByLogin(executorLogin)
                .orElseThrow(() -> new ExecutorAssignmentNotFoundException("Executor assignment not found."));

        List<ExecutorAssignment> activeAssignments =
                executorAssignmentRepository.findAllByExecutorUserIdAndStatus(user.getId(), ExecutorStatus.ACTIVE);

        if (activeAssignments.isEmpty()) {
            // Phân biệt: chưa từng được phân công (404) với đã phân công nhưng chưa kích hoạt / bị thu hồi (403)
            if (executorAssignmentRepository.findAllByExecutorUserId(user.getId()).isEmpty()) {
                throw new ExecutorAssignmentNotFoundException("Executor assignment not found.");
            }
            throw new ExecutorNotActiveException("Access denied. Executor has not been activated yet.");
        }

        // Một Executor có thể vừa là PRIMARY vừa là BACKUP của cùng Owner: giữ một assignment/Owner để không trùng Vault
        Map<UUID, ExecutorAssignment> assignmentByOwner = activeAssignments.stream()
                .collect(Collectors.toMap(a -> a.getOwner().getId(), Function.identity(),
                        (first, second) -> first, LinkedHashMap::new));

        // Không lọc theo trạng thái Vault: CLAIMED / ARCHIVED_LOCKED vẫn trả về kèm vaultStatus
        return vaultRepository.findAllByOwnerIdIn(assignmentByOwner.keySet()).stream()
                .map(vault -> mapToAssignedVaultResponse(vault, assignmentByOwner.get(vault.getOwner().getId())))
                .toList();
    }

    private AssignedVaultResponse mapToAssignedVaultResponse(Vault vault, ExecutorAssignment assignment) {
        List<DigitalAsset> activeAssets =
                digitalAssetRepository.findByVaultIdAndStatus(vault.getId(), AssetStatus.ACTIVE);
        List<AssetType> assetTypes = activeAssets.stream()
                .map(DigitalAsset::getAssetType)
                .distinct()
                .toList();

        User owner = vault.getOwner();
        User beneficiary = vault.getBeneficiary();

        return AssignedVaultResponse.builder()
                .vaultId(vault.getId())
                .vaultName(vault.getName())
                .description(vault.getDescription())
                .vaultStatus(vault.getStatus())
                .ownerId(owner.getId())
                .ownerName(owner.getFullName())
                .assetCount(activeAssets.size())
                .assetTypes(assetTypes)
                .beneficiaryEmailMasked(maskEmail(beneficiary != null ? beneficiary.getEmail() : null))
                .beneficiaryNameMasked(maskName(beneficiary != null ? beneficiary.getFullName() : null))
                .executorType(assignment.getExecutorType())
                .activatedAt(assignment.getActivatedAt())
                .build();
    }

    /**
     * Loads and maps all vaults belonging to the given owner to handover progress responses.
     * Extracted to avoid redundant DB queries when both getHandoverProgress overloads need the same data.
     */
    private List<VaultHandoverProgressResponse> fetchVaultProgress(UUID ownerId) {
        return vaultRepository.findAllByOwnerId(ownerId).stream()
                .map(this::mapToProgressResponse)
                .toList();
    }

    private void validateExecutorActive(ExecutorAssignment assignment) {
        if (assignment.getStatus() != ExecutorStatus.ACTIVE) {
            throw new ExecutorNotActiveException("Access denied. Executor has not been activated yet.");
        }
    }

    private VaultHandoverProgressResponse mapToProgressResponse(Vault vault) {
        long assetCount = digitalAssetRepository.countByVaultId(vault.getId());
        Optional<BeneficiaryClaim> claimOpt = beneficiaryClaimRepository.findByVaultId(vault.getId());

        // Vault-level deadline takes priority (explicitly set by owner).
        // Fall back to claim-level deadline only if vault has not configured one yet.
        LocalDateTime deadline = vault.getClaimDeadlineAt();
        if (deadline == null && claimOpt.isPresent()) {
            deadline = claimOpt.get().getClaimDeadlineAt();
        }

        boolean isVerified = identityVerificationRepository.existsByVaultIdAndStatus(
                vault.getId(), VerificationStatus.SUCCESS);

        HandoverStatus handoverStatus = determineHandoverStatus(vault, claimOpt, isVerified);

        String maskedEmail = maskEmail(vault.getBeneficiary() != null ? vault.getBeneficiary().getEmail() : null);
        String maskedName = maskName(vault.getBeneficiary() != null ? vault.getBeneficiary().getFullName() : null);

        return VaultHandoverProgressResponse.builder()
                .vaultId(vault.getId())
                .vaultName(vault.getName())
                .description(vault.getDescription())
                .handoverStatus(handoverStatus)
                .assetCount(assetCount)
                .claimDeadlineAt(deadline)
                .unlockedAt(vault.getUnlockedAt())
                .beneficiaryEmailMasked(maskedEmail)
                .beneficiaryNameMasked(maskedName)
                .build();
    }

    private HandoverStatus determineHandoverStatus(Vault vault, Optional<BeneficiaryClaim> claimOpt, boolean isVerified) {
        if (vault.getStatus() == VaultStatus.ARCHIVED_LOCKED) {
            return HandoverStatus.ARCHIVED_LOCKED;
        }
        if (vault.getStatus() == VaultStatus.CLAIMED ||
                (claimOpt.isPresent() && claimOpt.get().getStatus() == ClaimStatus.CLAIMED)) {
            return HandoverStatus.CLAIMED;
        }
        if (vault.getStatus() == VaultStatus.UNLOCKED) {
            return HandoverStatus.UNLOCKED;
        }
        if (isVerified) {
            return HandoverStatus.VERIFIED;
        }
        return HandoverStatus.PENDING;
    }

    private Optional<User> findUserByLogin(String login) {
        if (login == null || login.isBlank()) {
            return Optional.empty();
        }
        Optional<User> byEmail = userRepository.findByEmail(login);
        if (byEmail.isPresent()) {
            return byEmail;
        }
        try {
            UUID userId = UUID.fromString(login);
            return userRepository.findById(userId);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private boolean isSameUser(User user, String login) {
        if (user == null || login == null) {
            return false;
        }
        return login.equalsIgnoreCase(user.getEmail()) || login.equals(String.valueOf(user.getId()));
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return null;
        }
        int atIndex = email.indexOf('@');
        String username = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        if (username.length() <= 1) {
            return username + "***" + domain;
        }
        return username.charAt(0) + "***" + domain;
    }

    private String maskName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String[] parts = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (!part.isEmpty()) {
                sb.append(part.charAt(0)).append("***");
            }
            if (i < parts.length - 1) {
                sb.append(" ");
            }
        }
        return sb.toString();
    }
}
