package com.ltld.app.legacyvault.dto.vaultdto;

import com.ltld.app.legacyvault.enums.BeneficiaryInviteStatus;

import java.util.UUID;

public record VaultBeneficiaryResponse(UUID id, String email, String fullName,
                                       String relationship, BeneficiaryInviteStatus status) {
}
