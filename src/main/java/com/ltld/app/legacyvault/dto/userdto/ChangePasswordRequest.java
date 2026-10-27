package com.ltld.app.legacyvault.dto.userdto;

import com.ltld.app.legacyvault.utility.PasswordPolicy;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePasswordRequest {
    @NotBlank(message = "Please enter your current password")
    private String currentPassword;

    @NotBlank(message = "Please enter a new password")
    @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE)
    private String newPassword;

    @NotBlank(message = "Please confirm your new password")
    private String passwordConfirm;

    @AssertTrue(message = "Password confirmation does not match")
    public boolean isPasswordMatching() {
        if (newPassword == null || passwordConfirm == null) return true;
        return newPassword.equals(passwordConfirm);
    }
}