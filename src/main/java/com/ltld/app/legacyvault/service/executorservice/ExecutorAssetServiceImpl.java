package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.dto.executordto.ExecutorAssignedVaultsResponse;
import com.ltld.app.legacyvault.dto.executordto.ExecutorVaultAssetResponse;
import com.ltld.app.legacyvault.dto.executordto.ExecutorVaultBeneficiaryResponse;
import com.ltld.app.legacyvault.dto.executordto.ExecutorVaultResponse;
import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.AssetStatus;
import com.ltld.app.legacyvault.repository.DigitalAssetRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExecutorAssetServiceImpl implements ExecutorAssetService {

    private final ExecutorAccessGuard executorAccessGuard;
    private final VaultRepository vaultRepository;
    private final DigitalAssetRepository digitalAssetRepository;

    @Override
    @Transactional(readOnly = true)
    public ExecutorAssignedVaultsResponse getAssignedVaults(UUID assignmentId, String executorLogin) {
        ExecutorAssignment assignment = executorAccessGuard.requireActiveAssignment(assignmentId, executorLogin);

        UUID ownerId = assignment.getOwner().getId();
        String ownerFullName = assignment.getOwner().getFullName();

        List<Vault> vaults = vaultRepository.findByOwnerIdWithBeneficiary(ownerId);
        if (vaults.isEmpty()) {
            return ExecutorAssignedVaultsResponse.builder()
                    .assignmentId(assignmentId)
                    .ownerFullName(ownerFullName)
                    .vaults(Collections.emptyList())
                    .build();
        }

        List<UUID> vaultIds = vaults.stream().map(Vault::getId).toList();
        Map<UUID, List<DigitalAsset>> assetsByVaultId = digitalAssetRepository
                .findByVaultIdInAndStatus(vaultIds, AssetStatus.ACTIVE).stream()
                .filter(a -> a.getVault() != null && a.getVault().getId() != null)
                .collect(Collectors.groupingBy(a -> a.getVault().getId()));

        List<ExecutorVaultResponse> vaultResponses = vaults.stream()
                .map(vault -> toVaultResponse(vault, assetsByVaultId.getOrDefault(vault.getId(), Collections.emptyList())))
                .toList();

        return ExecutorAssignedVaultsResponse.builder()
                .assignmentId(assignmentId)
                .ownerFullName(ownerFullName)
                .vaults(vaultResponses)
                .build();
    }

    private static ExecutorVaultResponse toVaultResponse(Vault vault, List<DigitalAsset> assets) {
        ExecutorVaultBeneficiaryResponse beneficiary = vault.getBeneficiary() == null ? null
                : ExecutorVaultBeneficiaryResponse.builder()
                        .id(vault.getBeneficiary().getId())
                        .fullName(vault.getBeneficiary().getFullName())
                        .build();

        List<ExecutorVaultAssetResponse> assetResponses = assets.stream()
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
                .beneficiary(beneficiary)
                .assets(assetResponses)
                .build();
    }
}
