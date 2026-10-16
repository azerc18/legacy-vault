package com.ltld.app.legacyvault.service.vaultservice;

import com.ltld.app.legacyvault.dto.vaultdto.AdminVaultDetailResponse;
import com.ltld.app.legacyvault.dto.vaultdto.AssignBeneficiaryRequest;
import com.ltld.app.legacyvault.dto.vaultdto.AssignBeneficiaryResponse;
import com.ltld.app.legacyvault.dto.vaultdto.CreateVaultRequest;
import com.ltld.app.legacyvault.dto.vaultdto.UpdateVaultRequest;
import com.ltld.app.legacyvault.dto.vaultdto.VaultDetailResponse;
import com.ltld.app.legacyvault.dto.vaultdto.VaultResponse;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.VaultStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface VaultService {
    Vault createVault(UUID ownerId, CreateVaultRequest request) throws Exception;
    void deleteVault(UUID vaultId, UUID ownerId) throws Exception;
    AssignBeneficiaryResponse assignBeneficiary(UUID ownerId, UUID vaultId, AssignBeneficiaryRequest request);

    // FR-01/FR-27: Quản lý Vault cho Owner
    List<VaultResponse> getMyVaults(UUID ownerId);
    VaultDetailResponse getVaultDetail(UUID vaultId, UUID ownerId);
    VaultResponse updateVault(UUID vaultId, UUID ownerId, UpdateVaultRequest request);

    // FR-20/Admin: Quản trị Vault cho Admin
    Page<VaultResponse> getAllVaultsForAdmin(VaultStatus status, Pageable pageable);
    AdminVaultDetailResponse getAdminVaultDetail(UUID vaultId, UUID adminId);
}
