package com.ltld.app.legacyvault.dto.beneficiarydto;

import com.ltld.app.legacyvault.enums.VerificationMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.UUID;

@Data
public class VerifyIdentityRequest {
    @NotNull(message = "Vault ID không được để trống")
    private UUID vaultId;

    @NotNull(message = "Phương thức xác thực không được để trống")
    private VerificationMethod verificationMethod;

    // Bắt buộc khi verificationMethod = OTP (kiểm tra ở service)
    @Pattern(regexp = "\\d{6}", message = "OTP phải gồm đúng 6 chữ số")
    private String otp;

    // Bắt buộc khi verificationMethod = EKYC_MOCK (kiểm tra ở service)
    private String kycIdNumber;
}