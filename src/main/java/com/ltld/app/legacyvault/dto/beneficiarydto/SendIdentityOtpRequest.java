package com.ltld.app.legacyvault.dto.beneficiarydto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/** #19: body của POST /api/beneficiaries/verifications/otp. Gửi OTP luôn là phương thức OTP nên chỉ cần vaultId. */
@Data
public class SendIdentityOtpRequest {
    @NotNull(message = "Vault ID is required")
    private UUID vaultId;
}
