package com.ltld.app.legacyvault.dto.userdto;

import com.ltld.app.legacyvault.utility.PasswordPolicy;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

// Cài đặt mật khẩu lần đầu cho tài khoản tạo bằng Google (chưa có mật khẩu nên không hỏi mật khẩu hiện tại).
// Thay vào đó phải chứng minh sở hữu email bằng OTP (POST /users/me/set-password/otp), và mã 2FA nếu đã bật.
@Data
public class SetPasswordRequest {
    @NotBlank(message = "Please enter a new password")
    @Pattern(regexp = PasswordPolicy.REGEX, message = PasswordPolicy.MESSAGE)
    private String newPassword;

    @NotBlank(message = "Please confirm your new password")
    private String passwordConfirm;

    @NotBlank(message = "Please enter the code sent to your email")
    private String otp;

    // Mã 2FA hoặc mã dự phòng; chỉ bắt buộc khi tài khoản đã bật 2FA
    private String code;

    @AssertTrue(message = "Password confirmation does not match")
    public boolean isPasswordMatching() {
        if (newPassword == null || passwordConfirm == null) return true;
        return newPassword.equals(passwordConfirm);
    }
}
