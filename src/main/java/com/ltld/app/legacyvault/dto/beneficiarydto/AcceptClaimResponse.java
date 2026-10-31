package com.ltld.app.legacyvault.dto.beneficiarydto;

import com.ltld.app.legacyvault.enums.BeneficiaryInviteStatus;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class AcceptClaimResponse {
    private final UUID vaultId;
    private final BeneficiaryInviteStatus status;
}