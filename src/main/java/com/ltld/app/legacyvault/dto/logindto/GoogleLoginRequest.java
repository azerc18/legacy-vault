package com.ltld.app.legacyvault.dto.logindto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// credential = ID token (JWT) mà Google Identity Services trả cho FE
public record GoogleLoginRequest(
        @NotBlank(message = "This field is required") @Size(max = 4096) String credential) {}
