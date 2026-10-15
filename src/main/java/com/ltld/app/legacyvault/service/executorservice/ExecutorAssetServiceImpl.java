package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.dto.executordto.*;
import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.AssetStatus;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.exception.ExecutorAssignmentNotFoundException;
import com.ltld.app.legacyvault.exception.ExecutorNotActiveException;
import com.ltld.app.legacyvault.repository.DigitalAssetRepository;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutorAssetServiceImpl implements ExecutorAssetService {

    private final ExecutorAssignmentRepository executorAssignmentRepository;
    private final VaultRepository vaultRepository;
    private final DigitalAssetRepository digitalAssetRepository;

    @Override
    @Transactional(readOnly = true)
    public ExecutorAssignedVaultsResponse getAssignedVaults(UUID assignmentId, String executorLogin) {
        // (1) IDOR check: Tìm assignment và xác thực caller
        ExecutorAssignment assignment = executorAssignmentRepository.findById(assignmentId)
                .filter(a -> isSameUser(a.getExecutorUser(), executorLogin))
                .orElseThrow(() -> new ExecutorAssignmentNotFoundException("Executor assignment not found."));

        // (2) Trạng thái check: Chỉ cho phép khi assignment ACTIVE
        if (assignment.getStatus() != ExecutorStatus.ACTIVE) {
            throw new ExecutorNotActiveException("Chưa đến thời điểm thực hiện quyền");
        }

        UUID ownerId = assignment.getOwner().getId();
        String ownerFullName = assignment.getOwner().getFullName();

        // (3) Load danh sách Vault của Owner kèm Beneficiary (tránh N+1)
        List<Vault> vaults = vaultRepository.findByOwnerIdWithBeneficiary(ownerId);

        // (4) Nếu không có Vault nào, trả về danh sách rỗng (KHÔNG gọi query assets)
        if (vaults.isEmpty()) {
            return ExecutorAssignedVaultsResponse.builder()
                    .assignmentId(assignmentId)
                    .ownerFullName(ownerFullName)
                    .vaults(Collections.emptyList())
                    .build();
        }

        // (5) Load danh sách DigitalAsset ACTIVE theo danh sách vaultIds và gom nhóm theo vaultId (1 query duy nhất)
        List<UUID> vaultIds = vaults.stream().map(Vault::getId).toList();
        List<DigitalAsset> activeAssets = digitalAssetRepository.findByVaultIdInAndStatus(vaultIds, AssetStatus.ACTIVE);

        Map<UUID, List<DigitalAsset>> assetsByVaultId = activeAssets.stream()
                .filter(a -> a.getVault() != null && a.getVault().getId() != null)
                .collect(Collectors.groupingBy(a -> a.getVault().getId()));

        // (6) Map sang DTO
        List<ExecutorVaultResponse> vaultResponses = vaults.stream().map(vault -> {
            ExecutorVaultBeneficiaryResponse beneficiaryResponse = null;
            if (vault.getBeneficiary() != null) {
                beneficiaryResponse = ExecutorVaultBeneficiaryResponse.builder()
                        .id(vault.getBeneficiary().getId())
                        .fullName(vault.getBeneficiary().getFullName())
                        .build();
            }

            List<DigitalAsset> vaultAssets = assetsByVaultId.getOrDefault(vault.getId(), Collections.emptyList());
            List<ExecutorVaultAssetResponse> assetResponses = vaultAssets.stream()
                    .map(asset -> ExecutorVaultAssetResponse.builder()
                            .assetId(asset.getId())
                            .assetType(asset.getAssetType())
                            .assetName(asset.getAssetName())
                            .build())
                    .toList();

            return ExecutorVaultResponse.builder()
                    .vaultId(vault.getId())
                    .name(vault.getName())
                    .status(vault.getStatus())
                    .handoverInstructions(vault.getDescription())
                    .beneficiary(beneficiaryResponse)
                    .assets(assetResponses)
                    .build();
        }).toList();

        return ExecutorAssignedVaultsResponse.builder()
                .assignmentId(assignmentId)
                .ownerFullName(ownerFullName)
                .vaults(vaultResponses)
                .build();
    }

    private static boolean isSameUser(User user, String login) {
        if (user == null || login == null) {
            return false;
        }
        return login.equalsIgnoreCase(user.getEmail()) || login.equals(String.valueOf(user.getId()));
    }
}
