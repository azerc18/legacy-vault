package com.ltld.app.legacyvault.service.captchaservice;

/**
 * Kiểm CAPTCHA (Cloudflare Turnstile) ở các bước công khai tốn tài nguyên thật: Đăng ký, Quên mật khẩu,
 * và Đăng nhập khi tài khoản đã sai nhiều lần. KHÔNG dùng ở link check-in DMS / xác nhận chéo:
 * CAPTCHA lỗi ở đó có thể làm Owner không check-in được và DMS bàn giao khi Owner còn sống.
 */
public interface CaptchaService {

    enum Result { PASSED, FAILED, UNAVAILABLE }

    /** false khi cờ tắt hoặc thiếu key: mọi bước đều bỏ qua CAPTCHA. */
    boolean isEnabled();

    /** Site key công khai cho FE vẽ widget; null khi tắt. */
    String siteKey();

    /** Số lần sai mật khẩu liên tiếp của một tài khoản để bắt đầu đòi CAPTCHA ở Đăng nhập. */
    int loginAfterFailures();

    /** Gửi token lên Cloudflare. Token trống -> FAILED, không gọi mạng. */
    Result verify(String token);

    /**
     * Dùng cho Đăng ký và Quên mật khẩu (fail-closed): không qua -> CAPTCHA_INVALID,
     * không gọi được Cloudflare -> CAPTCHA_UNAVAILABLE. Tắt thì không làm gì.
     */
    void requirePassed(String token, String step);
}
