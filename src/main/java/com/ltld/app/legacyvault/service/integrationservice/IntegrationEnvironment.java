package com.ltld.app.legacyvault.service.integrationservice;

import com.ltld.app.legacyvault.dto.integrationdto.IntegrationsResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Đọc cấu hình tích hợp từ biến môi trường cho trang Tích hợp. Mọi key / secret chỉ được biến thành
 * "đã đặt / chưa đặt" ngay trong constructor, không giữ lại giá trị.
 */
@Component
public class IntegrationEnvironment {

    /** Ký xác nhận của Legal Verifier hiện xác thực bằng OTP gửi qua email (VerificationMethod MOCK_OTP). */
    static final String VERIFIER_AUTH_METHOD = "EMAIL_OTP";

    private final boolean kycMockEnvDefault;
    private final boolean kycMockToggleAllowed;
    private final boolean smsSandbox;
    private final IntegrationsResponse.Ai ai;
    private final IntegrationsResponse.Payments payments;
    private final IntegrationsResponse.Mail mail;
    private final IntegrationsResponse.Captcha captcha;
    private final IntegrationsResponse.GoogleLogin googleLogin;

    /**
     * @param kycMockToggleAllowed APP_KYC_MOCK_ADMIN_TOGGLE_ALLOWED: máy chủ có cho Admin BẬT eKYC mô phỏng từ trang
     *                             Tích hợp không. Mặc định false (bản thật); local / docker đặt true. Tắt thì luôn được.
     */
    public IntegrationEnvironment(@Value("${app.kyc.mock.enabled:false}") boolean kycMockEnvDefault,
                                  @Value("${app.kyc.mock.admin-toggle-allowed:false}") boolean kycMockToggleAllowed,
                                  @Value("${app.kyc.sms-sandbox:true}") boolean smsSandbox,
                                  @Value("${app.ai.provider:mock}") String aiProvider,
                                  @Value("${app.ai.gemini.api-key:}") String geminiKey,
                                  @Value("${app.ai.gemini.backup-api-key:}") String geminiBackupKey,
                                  @Value("${app.ai.groq.api-key:}") String groqKey,
                                  @Value("${app.ai.deepseek.api-key:}") String deepseekKey,
                                  @Value("${app.ai.ocr.demo-scenarios:false}") boolean ocrDemoScenarios,
                                  @Value("${app.vnpay.tmn-code:}") String vnpayTmn,
                                  @Value("${app.vnpay.hash-secret:}") String vnpaySecret,
                                  @Value("${app.vnpay.pay-url:}") String vnpayUrl,
                                  @Value("${app.paypal.client-id:}") String paypalClient,
                                  @Value("${app.paypal.secret:}") String paypalSecret,
                                  @Value("${app.paypal.base-url:}") String paypalUrl,
                                  @Value("${app.payment.reconcile.enabled:true}") boolean reconcileEnabled,
                                  @Value("${spring.mail.host:}") String mailHost,
                                  @Value("${spring.mail.username:}") String mailUser,
                                  @Value("${spring.mail.password:}") String mailPassword,
                                  @Value("${app.captcha.enabled:false}") boolean captchaEnabled,
                                  @Value("${app.captcha.site-key:}") String captchaSite,
                                  @Value("${app.captcha.secret-key:}") String captchaSecret,
                                  @Value("${app.google.client-id:}") String googleClient) {
        this.kycMockEnvDefault = kycMockEnvDefault;
        this.kycMockToggleAllowed = kycMockToggleAllowed;
        this.smsSandbox = smsSandbox;
        this.ai = new IntegrationsResponse.Ai(aiProvider == null ? "mock" : aiProvider.trim().toLowerCase(Locale.ROOT),
                set(geminiKey), set(geminiBackupKey), set(groqKey), set(deepseekKey), ocrDemoScenarios);
        this.payments = new IntegrationsResponse.Payments(
                new IntegrationsResponse.Gateway(set(vnpayTmn) && set(vnpaySecret), isSandbox(vnpayUrl)),
                new IntegrationsResponse.Gateway(set(paypalClient) && set(paypalSecret), isSandbox(paypalUrl)),
                reconcileEnabled);
        this.mail = new IntegrationsResponse.Mail(set(mailHost) ? mailHost.trim() : null, set(mailUser) && set(mailPassword));
        this.captcha = new IntegrationsResponse.Captcha(captchaEnabled, set(captchaSite), set(captchaSecret));
        this.googleLogin = new IntegrationsResponse.GoogleLogin(set(googleClient));
    }

    public boolean kycMockEnvDefault() { return kycMockEnvDefault; }

    public boolean kycMockToggleAllowed() { return kycMockToggleAllowed; }

    public boolean smsSandbox() { return smsSandbox; }

    public IntegrationsResponse.Ai ai() { return ai; }

    public IntegrationsResponse.Signature signature() {
        return new IntegrationsResponse.Signature(VERIFIER_AUTH_METHOD, false);
    }

    public IntegrationsResponse.Payments payments() { return payments; }

    public IntegrationsResponse.Mail mail() { return mail; }

    public IntegrationsResponse.Captcha captcha() { return captcha; }

    public IntegrationsResponse.GoogleLogin googleLogin() { return googleLogin; }

    private static boolean set(String s) {
        return s != null && !s.isBlank();
    }

    /** URL mặc định của cả VNPay lẫn PayPal đều là sandbox; URL thật không chứa "sandbox". */
    static boolean isSandbox(String url) {
        return url == null || url.isBlank() || url.toLowerCase(Locale.ROOT).contains("sandbox");
    }
}
