package com.ltld.app.legacyvault.dto.logindto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MfaLoginRequest(
        @NotBlank(message = "This field is required") String mfaToken,
        @NotBlank(message = "This field is required") @Size(max = 20) String code) {}