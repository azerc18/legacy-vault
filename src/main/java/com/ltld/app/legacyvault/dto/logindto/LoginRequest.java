package com.ltld.app.legacyvault.dto.logindto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank(message = "This field is required")
    private String email;

    @NotBlank(message = "This field is required")
    private String password;

    // Token Cloudflare Turnstile; chỉ bắt buộc khi BE bật CAPTCHA (GET /api/auth/captcha/config)
    @Size(max = 2048)
    private String captchaToken;
}
