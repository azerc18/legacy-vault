package com.ltld.app.legacyvault.dto.executordto;

import com.ltld.app.legacyvault.enums.VaultStatus;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class ExecutorVaultResponse {
    private final UUID vaultId;
    private final String name;
    private final VaultStatus status;
    /** Lấy từ {@code Vault.description} do Owner nhập. */
    private final String handoverInstructions;
    private final ExecutorVaultBeneficiaryResponse beneficiary;
    private final List<ExecutorVaultAssetResponse> assets;
}
