package com.ltld.app.legacyvault.service.paymentservice;

import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * T1: Tích hợp VNPay Sandbox v2.1.0 (tạo URL thanh toán + xác thực chữ ký HMAC-SHA512).
 * Hỗ trợ tự động suy ra vnp_ReturnUrl từ FRONTEND_BASE_URL khi deploy chung 1 container trên Azure (D8a/D8b).
 */
@Component
public class VnpayClient {

    private static final DateTimeFormatter VNP_TIME_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final String tmnCode;
    private final String hashSecret;
    private final String payUrl;
    private final String returnUrl;

    @Autowired
    public VnpayClient(
            @Value("${VNPAY_TMN_CODE:}") String tmnCode,
            @Value("${VNPAY_HASH_SECRET:}") String hashSecret,
            @Value("${VNPAY_PAY_URL:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}") String payUrl,
            @Value("${VNPAY_RETURN_URL:}") String returnUrl,
            @Value("${app.frontend.base-url:${FRONTEND_BASE_URL:http://localhost:5173}}") String frontendBaseUrl) {
        this.tmnCode = tmnCode != null ? tmnCode.trim() : "";
        this.hashSecret = hashSecret != null ? hashSecret.trim() : "";
        this.payUrl = (payUrl != null && !payUrl.isBlank()) ? payUrl.trim() : "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
        this.returnUrl = resolveReturnUrl(returnUrl, frontendBaseUrl);
    }

    public VnpayClient(String tmnCode, String hashSecret, String payUrl, String returnUrl) {
        this(tmnCode, hashSecret, payUrl, returnUrl, "http://localhost:5173");
    }

    public boolean isConfigured() {
        return !tmnCode.isBlank() && !hashSecret.isBlank();
    }

    public void ensureConfigured() {
        if (!isConfigured()) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "VNPay payment gateway is not configured.");
        }
    }

    static String resolveReturnUrl(String explicitReturnUrl, String frontendBaseUrl) {
        if (explicitReturnUrl != null && !explicitReturnUrl.isBlank()) {
            return explicitReturnUrl.trim();
        }
        if (frontendBaseUrl != null && !frontendBaseUrl.isBlank()) {
            String trimmed = frontendBaseUrl.trim().replaceAll("/+$", "");
            if (!trimmed.contains("localhost:5173")
                    && !trimmed.contains("127.0.0.1:5173")
                    && !trimmed.contains("localhost:3000")
                    && !trimmed.contains("127.0.0.1:3000")) {
                return trimmed + "/api/payments/vnpay-return";
            }
        }
        return "http://localhost:8084/api/payments/vnpay-return";
    }

    public String buildPaymentUrl(UUID txnId, long amountVnd, String orderInfo, String ipAddress) {
        ensureConfigured();

        ZonedDateTime now = ZonedDateTime.now(VN_ZONE);
        ZonedDateTime expire = now.plusMinutes(15);

        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", tmnCode);
        params.put("vnp_Amount", String.valueOf(amountVnd * 100L));
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", txnId.toString());
        params.put("vnp_OrderInfo", orderInfo != null ? orderInfo : ("LegacyVault subscription " + txnId));
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", returnUrl);
        params.put("vnp_IpAddr", (ipAddress == null || ipAddress.isBlank()) ? "127.0.0.1" : ipAddress);
        params.put("vnp_CreateDate", now.format(VNP_TIME_FMT));
        params.put("vnp_ExpireDate", expire.format(VNP_TIME_FMT));

        String query = buildSortedQueryString(params);
        String secureHash = hmacSha512(hashSecret, query);
        return payUrl + "?" + query + "&vnp_SecureHash=" + secureHash;
    }

    public boolean verify(Map<String, String> rawParams) {
        if (!isConfigured() || rawParams == null || rawParams.isEmpty()) {
            return false;
        }
        String receivedHash = rawParams.get("vnp_SecureHash");
        if (receivedHash == null || receivedHash.isBlank()) {
            return false;
        }
        String computed = signParams(rawParams);
        return MessageDigest.isEqual(
                computed.toLowerCase().getBytes(StandardCharsets.UTF_8),
                receivedHash.trim().toLowerCase().getBytes(StandardCharsets.UTF_8)
        );
    }

    public String signParams(Map<String, String> rawParams) {
        ensureConfigured();
        Map<String, String> sorted = new TreeMap<>();
        for (Map.Entry<String, String> entry : rawParams.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || value == null || value.isBlank()) {
                continue;
            }
            if ("vnp_SecureHash".equalsIgnoreCase(key) || "vnp_SecureHashType".equalsIgnoreCase(key)) {
                continue;
            }
            sorted.put(key, value);
        }
        String hashData = buildSortedQueryString(sorted);
        return hmacSha512(hashSecret, hashData);
    }

    private String buildSortedQueryString(Map<String, String> sortedParams) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : sortedParams.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (value == null || value.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(key, StandardCharsets.US_ASCII))
              .append('=')
              .append(URLEncoder.encode(value, StandardCharsets.US_ASCII));
        }
        return sb.toString();
    }

    public static String hmacSha512(String key, String data) {
        try {
            Mac hmac512 = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            hmac512.init(secretKey);
            byte[] bytes = hmac512.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hash = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hash.append(String.format("%02x", b & 0xff));
            }
            return hash.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute HMAC-SHA512 signature", e);
        }
    }
}
