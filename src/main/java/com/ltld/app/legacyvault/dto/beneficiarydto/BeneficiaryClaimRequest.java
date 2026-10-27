package com.ltld.app.legacyvault.dto.beneficiarydto;

import com.ltld.app.legacyvault.enums.VerificationMethod;
import lombok.Data;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Data
public class BeneficiaryClaimRequest {
    @NotNull(message = "Vault ID is required")
    private UUID vaultId;

    // Yêu cầu Frontend phải truyền lên phương thức xác thực (OTP hoặc EKYC_MOCK)
    @NotNull(message = "Verification method is required")
    private VerificationMethod verificationMethod;
}
