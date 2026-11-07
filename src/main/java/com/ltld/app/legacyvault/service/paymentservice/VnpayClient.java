package com.ltld.app.legacyvault.service.paymentservice;

import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
 * Cấu hình đọc từ {@link VnpayProperties} (app.vnpay.*); thiếu cấu hình không làm app dừng khởi động.
 */
@Slf4j
@Component
@EnableConfigurationProperties(VnpayProperties.class)
public class VnpayClient {

    private static final DateTimeFormatter VNP_TIME_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final String tmnCode;
    private final String hashSecret;
    private final String payUrl;
    private final String returnUrl;

    @Autowired
    public VnpayClient(VnpayProperties properties, @Value("${app.frontend.base-url}") String frontendBaseUrl) {
        this.tmnCode = properties.tmnCode();
        this.hashSecret = properties.hashSecret();
        this.payUrl = properties.payUrl();
        this.returnUrl = resolveReturnUrl(properties.returnUrl(), frontendBaseUrl);
        // Bean singleton: cảnh báo đúng 1 lần lúc khởi động, không chặn app chạy
        if (!properties.isConfigured()) {
            log.warn("T1: VNPay chưa cấu hình (app.vnpay.tmn-code / app.vnpay.hash-secret trống). "
                    + "App vẫn chạy nhưng mọi lần tạo thanh toán sẽ trả SERVICE_UNAVAILABLE.");
        }
    }

    public VnpayClient(VnpayProperties properties) {
        this(properties, "http://localhost:5173");
    }

    public boolean isConfigured() {
        return !tmnCode.isEmpty() && !hashSecret.isEmpty();
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
        params.put("vnp_TxnRef", toTxnRef(txnId));
        params.put("vnp_OrderInfo", sanitizeOrderInfo(orderInfo, txnId));
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", returnUrl);
        params.put("vnp_IpAddr", normalizeIpv4(ipAddress));
        params.put("vnp_CreateDate", now.format(VNP_TIME_FMT));
        params.put("vnp_ExpireDate", expire.format(VNP_TIME_FMT));

        String query = buildSortedQueryString(params);
        String secureHash = hmacSha512(hashSecret, query);
        return payUrl + "?" + query + "&vnp_SecureHash=" + secureHash;
    }

    // VNPay trả "Invalid data format" (mã 03) khi tham số có ký tự lạ: chỉ gửi chữ/số cho TxnRef và OrderInfo,
    // và IPv4 thuần cho IpAddr (không IPv6 như 0:0:0:0:0:0:0:1 khi chạy local, không kèm :port như sau proxy Azure).

    /** UUID -> 32 ký tự hex, không dấu gạch. */
    static String toTxnRef(UUID txnId) {
        return txnId.toString().replace("-", "");
    }

    /** Đọc lại vnp_TxnRef: nhận cả 32 hex (định dạng mới) lẫn UUID có gạch (đơn tạo trước bản sửa). Sai định dạng -> null. */
    public static UUID parseTxnRef(String txnRef) {
        if (txnRef == null || txnRef.isBlank()) {
            return null;
        }
        String ref = txnRef.trim();
        if (ref.matches("[0-9a-fA-F]{32}")) {
            ref = ref.substring(0, 8) + "-" + ref.substring(8, 12) + "-" + ref.substring(12, 16) + "-"
                    + ref.substring(16, 20) + "-" + ref.substring(20);
        }
        try {
            return UUID.fromString(ref);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Chỉ giữ chữ cái không dấu, số và khoảng trắng; trống thì dùng mô tả mặc định. */
    static String sanitizeOrderInfo(String orderInfo, UUID txnId) {
        String raw = (orderInfo == null || orderInfo.isBlank()) ? "LegacyVault subscription " + toTxnRef(txnId) : orderInfo;
        String ascii = java.text.Normalizer.normalize(raw, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd').replace('Đ', 'D');
        String cleaned = ascii.replaceAll("[^A-Za-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
        if (cleaned.isEmpty()) {
            cleaned = "LegacyVault subscription " + toTxnRef(txnId);
        }
        return cleaned.length() > 255 ? cleaned.substring(0, 255).trim() : cleaned;
    }

    /**
     * IPv4 thuần; bỏ ":port"; không phải IPv4 (IPv6, rỗng, rác) -> 127.0.0.1.
     * Lưu ý production: người dùng IPv6 thật cũng bị gửi 127.0.0.1; VNPay dùng vnp_IpAddr để chấm rủi ro,
     * nên trước khi lên production cần xác nhận với VNPay định dạng IPv6 được chấp nhận rồi cho đi qua.
     */
    static String normalizeIpv4(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return "127.0.0.1";
        }
        String ip = ipAddress.trim();
        if (ip.matches("\\d{1,3}(\\.\\d{1,3}){3}:\\d+")) {
            ip = ip.substring(0, ip.indexOf(':'));
        }
        if (ip.startsWith("::ffff:") && ip.substring(7).matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
            ip = ip.substring(7);
        }
        if (!ip.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
            return "127.0.0.1";
        }
        for (String part : ip.split("\\.")) {
            if (Integer.parseInt(part) > 255) {
                return "127.0.0.1";
            }
        }
        return ip;
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
