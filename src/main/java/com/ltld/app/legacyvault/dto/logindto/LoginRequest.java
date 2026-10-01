package com.ltld.app.legacyvault.dto.logindto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank(message = "This field is required")
    private String email;

    @NotBlank(message = "This field is required")
    private String password;
}
