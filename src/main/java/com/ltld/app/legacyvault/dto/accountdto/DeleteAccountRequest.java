package com.ltld.app.legacyvault.dto.accountdto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

// Xác nhận xoá tài khoản: mật khẩu hiện tại + gõ lại đúng email; bật 2FA thì thêm mã TOTP / mã dự phòng
@Data
public class DeleteAccountRequest {
    @NotBlank(message = "Please enter your current password")
    private String password;

    @NotBlank(message = "Please type your email to confirm")
    private String confirmEmail;

    private String code;
}
