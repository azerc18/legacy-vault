package com.ltld.app.legacyvault.dto.forgotpassworddto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

    // Token Cloudflare Turnstile; chỉ bắt buộc khi BE bật CAPTCHA (GET /api/auth/captcha/config)
    @Size(max = 2048)
    private String captchaToken;

    public ForgotPasswordRequest(String email) {
        this.email = email;
    }
}
