package com.ltld.app.legacyvault.beneficiary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.UUID;

@Data
public class VerifyIdentityRequest {
    @NotNull(message = "Vault ID không được để trống")
    private UUID vaultId;

    @NotBlank(message = "Mã xác thực không được để trống")
    private String authCode; // Dùng chung cho mã OTP hoặc chuỗi token EKYC mock
}