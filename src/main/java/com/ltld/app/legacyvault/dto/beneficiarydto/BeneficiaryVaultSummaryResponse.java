package com.ltld.app.legacyvault.dto.beneficiarydto;

import com.ltld.app.legacyvault.enums.BeneficiaryInviteStatus;
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
    private BeneficiaryInviteStatus myStatus;   // N2b: để FE hiện "Đã từ chối" và không cho mở
    private LocalDateTime unlockedAt;
    private LocalDateTime claimDeadlineAt;
    private LocalDateTime claimedAt;
}
