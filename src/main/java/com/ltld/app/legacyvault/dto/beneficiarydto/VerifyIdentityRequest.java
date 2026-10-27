package com.ltld.app.legacyvault.dto.beneficiarydto;

import com.ltld.app.legacyvault.enums.VerificationMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.UUID;

@Data
public class VerifyIdentityRequest {
    @NotNull(message = "Vault ID is required")
    private UUID vaultId;

    @NotNull(message = "Verification method is required")
    private VerificationMethod verificationMethod;

    // Bắt buộc khi verificationMethod = OTP (kiểm tra ở service)
    @Pattern(regexp = "\\d{6}", message = "OTP must be exactly 6 digits")
    private String otp;

    // Bắt buộc khi verificationMethod = EKYC_MOCK (kiểm tra ở service)
    private String kycIdNumber;
}