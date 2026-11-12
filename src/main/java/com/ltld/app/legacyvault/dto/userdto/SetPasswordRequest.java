package com.ltld.app.legacyvault.dto.userdto;

import com.ltld.app.legacyvault.utility.PasswordPolicy;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

// Cài đặt mật khẩu lần đầu cho tài khoản tạo bằng Google (chưa có mật khẩu nên không hỏi mật khẩu hiện tại)
@Data
public class SetPasswordRequest {
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
