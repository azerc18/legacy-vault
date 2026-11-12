package com.ltld.app.legacyvault.service.captchaservice;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

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
    /**
     * Hostname mà Cloudflare báo token được giải trên đó phải nằm trong danh sách này
     * (chặn token lấy từ trang khác dùng chung site key). Để trống thì bỏ qua bước này và chỉ dựa vào
     * danh sách hostname khai trên Cloudflare dashboard.
     */
    private List<String> allowedHostnames = new ArrayList<>();
    private Duration timeout = Duration.ofSeconds(5);
}
