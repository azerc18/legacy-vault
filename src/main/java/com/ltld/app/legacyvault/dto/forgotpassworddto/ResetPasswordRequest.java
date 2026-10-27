package com.ltld.app.legacyvault.dto.forgotpassworddto;

import com.ltld.app.legacyvault.utility.PasswordPolicy;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class ResetPasswordRequest {
    @Email(message = "Must be email format")
    @NotBlank(message = "This field is required")
    private String email;

    @NotBlank(message = "This field is required")
    @Pattern(regexp = "\\d{6}", message = "OTP must be exactly 6 digits.")
    private String otp;

    @NotBlank(message = "This field is required")
    @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE)
    private String newPassword;

    @NotBlank(message = "This field is required.")
    private String passwordConfirm;

    @AssertTrue(message = "Password is not matching.")
    public boolean isPasswordMatching() {
        if(newPassword == null || passwordConfirm == null) return true;
        return passwordConfirm.equals(newPassword);
    }
}
