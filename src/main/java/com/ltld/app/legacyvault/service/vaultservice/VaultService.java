package com.ltld.app.legacyvault.service.vaultservice;

import com.ltld.app.legacyvault.dto.vaultdto.CreateVaultRequest;
import com.ltld.app.legacyvault.entity.Vault;
import java.util.UUID;

public interface VaultService {
    Vault createVault(UUID ownerId, CreateVaultRequest request) throws Exception;
    void deleteVault(UUID vaultId, UUID ownerId) throws Exception;
}
