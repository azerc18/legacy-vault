package com.ltld.app.legacyvault.exception;

/**
 * Đăng nhập: tài khoản đã sai mật khẩu nhiều lần nên lần sau phải kèm CAPTCHA.
 * Trả kèm data.captchaRequired=true để FE hiện widget Turnstile.
 * Mã có thể là INVALID_CREDENTIALS (vừa sai thêm một lần) hoặc CAPTCHA_REQUIRED (thiếu / sai token, lần thử không được tính).
 */
public class CaptchaChallengeException extends BusinessException {
    public CaptchaChallengeException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
