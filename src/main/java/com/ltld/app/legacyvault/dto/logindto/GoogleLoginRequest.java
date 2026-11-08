package com.ltld.app.legacyvault.dto.logindto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// credential = ID token (JWT) mà Google Identity Services trả cho FE
// language = ngôn ngữ giao diện đang xem (vi | en), chỉ dùng khi lần đăng nhập này tạo tài khoản mới; trống = tiếng Việt
public record GoogleLoginRequest(
        @NotBlank(message = "This field is required") @Size(max = 4096) String credential,
        @Pattern(regexp = "^(vi|en)?$", message = "Language must be vi or en") String language) {

    public GoogleLoginRequest(String credential) {
        this(credential, null);
    }
}
