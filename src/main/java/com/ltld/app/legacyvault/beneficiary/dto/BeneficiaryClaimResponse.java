package com.ltld.app.legacyvault.beneficiary.dto;


import com.ltld.app.legacyvault.beneficiary.enums.ClaimStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class BeneficiaryClaimResponse {
    private UUID claimId;
    private UUID vaultId;
    private ClaimStatus status;
    private LocalDateTime claimDeadlineAt;
    private LocalDateTime claimedAt;
}
