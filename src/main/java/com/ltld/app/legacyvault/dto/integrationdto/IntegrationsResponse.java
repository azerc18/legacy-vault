package com.ltld.app.legacyvault.dto.integrationdto;

import java.time.LocalDateTime;

/**
 * Trang Tích hợp: trạng thái từng dịch vụ bên ngoài. Chỉ có "đã cấu hình / chưa", chế độ sandbox / thật,
 * KHÔNG BAO GIỜ có key, secret, mật khẩu hay địa chỉ email đăng nhập.
 */
public record IntegrationsResponse(Kyc kyc, Ai ai, Signature signature, Payments payments, Mail mail,
                                   Captcha captcha, GoogleLogin googleLogin) {

    /**
     * @param mockEnabled   đang có hiệu lực (sau khi xét lựa chọn của Admin)
     * @param mockOverride  lựa chọn của Admin; null = theo biến môi trường
     * @param envDefault    giá trị APP_KYC_MOCK_ENABLED
     * @param smsSandbox    KYC_SMS_SANDBOX: mã OTP số điện thoại hiện trên màn hình thay vì gửi SMS thật
     */
    public record Kyc(boolean mockEnabled, Boolean mockOverride, boolean envDefault, boolean smsSandbox,
                      LocalDateTime updatedAt, String updatedByEmail) {
    }

    /** OCR giấy chứng tử / CCCD và trợ lý Vaulty. Chi tiết model ở trang Cấu hình AI. */
    public record Ai(String provider, boolean geminiKeyConfigured, boolean geminiBackupKeyConfigured,
                     boolean groqKeyConfigured, boolean deepseekKeyConfigured, boolean ocrDemoScenarios) {
    }

    /** Ký xác nhận của Legal Verifier: xác thực bằng OTP qua email; chưa nối nhà cung cấp chữ ký số CA. */
    public record Signature(String verifierAuthMethod, boolean caProviderIntegrated) {
    }

    public record Payments(Gateway vnpay, Gateway paypal, boolean reconcileEnabled) {
    }

    /** sandbox suy từ URL cổng (chứa "sandbox"). */
    public record Gateway(boolean configured, boolean sandbox) {
    }

    /** host là địa chỉ SMTP công khai (vd. smtp.gmail.com); tài khoản đăng nhập không trả về. */
    public record Mail(String host, boolean configured) {
    }

    public record Captcha(boolean enabled, boolean siteKeyConfigured, boolean secretKeyConfigured) {
    }

    public record GoogleLogin(boolean configured) {
    }
}
