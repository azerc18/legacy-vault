package com.ltld.app.legacyvault.dto.mfadto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Mã TOTP 6 số hoặc mã dự phòng dạng XXXX-XXXX
public record MfaCodeRequest(
        @NotBlank(message = "Code is required")
        @Size(max = 20, message = "Code is too long")
        String code) {}