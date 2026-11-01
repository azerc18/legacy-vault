package com.ltld.app.legacyvault.service.adminuserservice;

import com.ltld.app.legacyvault.dto.adminuserdto.ClaimExtensionResponse;

import java.util.UUID;

public interface AdminClaimService {
    ClaimExtensionResponse extendClaim(UUID adminId, UUID vaultId, int days, String reason);
}
