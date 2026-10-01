package com.ltld.app.legacyvault.beneficiary.dto;

import com.ltld.app.legacyvault.beneficiary.enums.VerificationMethod;
import lombok.Data;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Data
public class BeneficiaryClaimRequest {
    @NotNull(message = "Vault ID không được để trống")
    private UUID vaultId;

    // Yêu cầu Frontend phải truyền lên phương thức xác thực (OTP hoặc EKYC_MOCK)
    @NotNull(message = "Phương thức xác thực không được để trống")
    private VerificationMethod verificationMethod;
}
