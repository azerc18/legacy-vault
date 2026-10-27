package com.ltld.app.legacyvault.dto.userdto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePasswordRequest {
    @NotBlank(message = "Please enter your current password")
    private String currentPassword;

    @NotBlank(message = "Please enter a new password")
    @Size(min = 6, max = 72, message = "New password must be 6-72 characters")
    private String newPassword;

    @NotBlank(message = "Please confirm your new password")
    private String passwordConfirm;

    @AssertTrue(message = "Password confirmation does not match")
    public boolean isPasswordMatching() {
        if (newPassword == null || passwordConfirm == null) return true;
        return newPassword.equals(passwordConfirm);
    }
}