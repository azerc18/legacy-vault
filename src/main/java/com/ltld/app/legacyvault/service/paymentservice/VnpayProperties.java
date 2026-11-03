package com.ltld.app.legacyvault.service.paymentservice;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * T1: cấu hình VNPay, đọc từ các key app.vnpay.* (tmn-code, hash-secret, pay-url, return-url, activate-on-return).
 *
 * <p>Không bắt buộc khi khởi động: thiếu tmnCode/hashSecret thì app vẫn chạy, {@link VnpayClient} log.warn một lần
 * và mọi lần tạo thanh toán trả lỗi SERVICE_UNAVAILABLE.
 *
 * @param tmnCode          mã website (TMN code) từ cổng merchant
 * @param hashSecret       khóa bí mật ký HMAC-SHA512
 * @param payUrl           URL cổng thanh toán; trống thì dùng URL sandbox
 * @param returnUrl        URL BACKEND (/api/payments/vnpay-return) VNPay redirect trình duyệt về; trống thì suy ra
 * @param activateOnReturn dev local: cho Return URL kích hoạt gói (mặc định false, chỉ IPN được kích hoạt)
 */
@ConfigurationProperties("app.vnpay")
public record VnpayProperties(String tmnCode,
                              String hashSecret,
                              String payUrl,
                              String returnUrl,
                              boolean activateOnReturn) {

    public static final String SANDBOX_PAY_URL = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";

    public VnpayProperties {
        tmnCode = trimToEmpty(tmnCode);
        hashSecret = trimToEmpty(hashSecret);
        payUrl = trimToEmpty(payUrl).isEmpty() ? SANDBOX_PAY_URL : payUrl.trim();
        returnUrl = trimToEmpty(returnUrl);
    }

    public boolean isConfigured() {
        return !tmnCode.isEmpty() && !hashSecret.isEmpty();
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
