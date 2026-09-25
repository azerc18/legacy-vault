package com.ltld.app.legacyvault.dto.otpdto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class VerifyOtpRequest {
    @Email(message = "This is not a valid email format.")
    @NotBlank(message = "This field is required")
    private String email;

    @NotBlank(message = "This field is required")
    @Pattern(regexp = "\\d{6}", message = "OTP must be exactly 6 digits.")
    private String otp;
}
