package com.ltld.app.legacyvault.service.paymentservice;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình PayPal (app.paypal.*). Thiếu client id/secret thì app vẫn chạy và cổng PayPal tự ẩn.
 *
 * @param baseUrl   API PayPal; trống thì dùng sandbox
 * @param vndPerUsd tỷ giá cố định để quy VND sang USD, vì PayPal không nhận VND
 */
@ConfigurationProperties("app.paypal")
public record PaypalProperties(String clientId, String secret, String baseUrl, long vndPerUsd) {

    public static final String SANDBOX_BASE_URL = "https://api-m.sandbox.paypal.com";
    public static final long DEFAULT_VND_PER_USD = 25_400L;

    public PaypalProperties {
        clientId = clientId == null ? "" : clientId.trim();
        secret = secret == null ? "" : secret.trim();
        baseUrl = baseUrl == null || baseUrl.isBlank() ? SANDBOX_BASE_URL : baseUrl.trim();
        vndPerUsd = vndPerUsd > 0 ? vndPerUsd : DEFAULT_VND_PER_USD;
    }

    public boolean isConfigured() {
        return !clientId.isEmpty() && !secret.isEmpty();
    }
}
