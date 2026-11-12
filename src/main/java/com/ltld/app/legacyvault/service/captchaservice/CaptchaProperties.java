package com.ltld.app.legacyvault.service.captchaservice;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.captcha")
public class CaptchaProperties {
    /** Cờ tổng. Mặc định tắt (local, CI, E2E); production bật bằng APP_CAPTCHA_ENABLED=true. */
    private boolean enabled = false;
    /** Site key (công khai), lấy ở Cloudflare dashboard -> Turnstile. */
    private String siteKey = "";
    /** Secret key: CHỈ đặt qua biến môi trường APP_CAPTCHA_SECRET_KEY, không commit. */
    private String secretKey = "";
    private String verifyUrl = "https://challenges.cloudflare.com/turnstile/v0/siteverify";
    private int loginAfterFailures = 3;
    private Duration timeout = Duration.ofSeconds(5);
}
