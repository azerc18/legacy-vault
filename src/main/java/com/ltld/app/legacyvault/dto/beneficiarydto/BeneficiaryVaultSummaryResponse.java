package com.ltld.app.legacyvault.dto.beneficiarydto;

import com.ltld.app.legacyvault.enums.ClaimStatus;
import com.ltld.app.legacyvault.enums.VaultStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class BeneficiaryVaultSummaryResponse {
    private UUID vaultId;
    private String vaultName;
    private String ownerName;            // tên Owner, để Beneficiary biết hồ sơ của ai
    private VaultStatus vaultStatus;     // UNLOCKED / CLAIMED / ARCHIVED_LOCKED
    private ClaimStatus claimStatus;     // null nếu chưa có claim
    private LocalDateTime unlockedAt;
    private LocalDateTime claimDeadlineAt;
    private LocalDateTime claimedAt;
}