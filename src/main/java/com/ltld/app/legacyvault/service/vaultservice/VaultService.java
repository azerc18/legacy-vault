package com.ltld.app.legacyvault.service.vaultservice;

import com.ltld.app.legacyvault.dto.vaultdto.AssignBeneficiaryRequest;
import com.ltld.app.legacyvault.dto.vaultdto.AssignBeneficiaryResponse;
import com.ltld.app.legacyvault.dto.vaultdto.CreateVaultRequest;
import com.ltld.app.legacyvault.dto.vaultdto.VaultResponse;
import com.ltld.app.legacyvault.entity.Vault;

import java.util.List;
import java.util.UUID;

public interface VaultService {
    Vault createVault(UUID ownerId, CreateVaultRequest request) throws Exception;
    void deleteVault(UUID vaultId, UUID ownerId) throws Exception;
    AssignBeneficiaryResponse assignBeneficiary(UUID ownerId, UUID vaultId, AssignBeneficiaryRequest request);
    List<VaultResponse> getMyVaults(UUID ownerId);
    com.ltld.app.legacyvault.dto.vaultdto.VaultDetailResponse getVaultDetail(UUID vaultId, UUID requesterId, boolean isAdmin);
    com.ltld.app.legacyvault.dto.vaultdto.VaultResponse updateVault(UUID vaultId, UUID ownerId, com.ltld.app.legacyvault.dto.vaultdto.UpdateVaultRequest request);
    org.springframework.data.domain.Page<com.ltld.app.legacyvault.dto.vaultdto.VaultResponse> getAllVaultsForAdmin(com.ltld.app.legacyvault.enums.VaultStatus status, org.springframework.data.domain.Pageable pageable);

}
