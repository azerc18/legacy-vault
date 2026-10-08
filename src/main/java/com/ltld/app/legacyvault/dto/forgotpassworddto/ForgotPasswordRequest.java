package com.ltld.app.legacyvault.dto.forgotpassworddto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class ForgotPasswordRequest {
    @Email(message = "Must be email format")
    @NotBlank(message = "This field is required")
    private String email;
}
