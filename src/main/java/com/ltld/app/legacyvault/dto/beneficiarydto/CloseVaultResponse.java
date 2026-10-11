package com.ltld.app.legacyvault.dto.beneficiarydto;

import com.ltld.app.legacyvault.enums.ClaimStatus;
import com.ltld.app.legacyvault.enums.VaultStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class CloseVaultResponse {
    private UUID vaultId;
    private VaultStatus vaultStatus;
    private ClaimStatus claimStatus;
    private LocalDateTime claimedAt;
}