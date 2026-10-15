package com.ltld.app.legacyvault.dto.beneficiarydto;

import com.ltld.app.legacyvault.enums.ClaimStatus;
import com.ltld.app.legacyvault.enums.VaultStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class BeneficiaryVaultSummaryResponse {
    private UUID vaultId;
    private String vaultName;
    private String ownerName;
    private VaultStatus vaultStatus;
    private ClaimStatus claimStatus;
    private LocalDateTime unlockedAt;
    private LocalDateTime claimDeadlineAt;
    private LocalDateTime claimedAt;
}
