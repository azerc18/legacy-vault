package com.ltld.app.legacyvault.service.paymentservice;

import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
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
    /** VNPay huỷ phiên thanh toán sau ngần này phút (vnp_ExpireDate). */
    public static final int PAYMENT_URL_TTL_MINUTES = 15;
    static final String SANDBOX_API_URL = "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction";
    static final String PRODUCTION_API_URL = "https://merchant.vnpay.vn/merchant_webapi/api/transaction";

    private final String tmnCode;
    private final String hashSecret;
    private final String payUrl;
    private final String returnUrl;
    private final String apiUrl;
    private final RestClient restClient;

    @Autowired
    public VnpayClient(VnpayProperties properties,
                       @Value("${app.frontend.base-url}") String frontendBaseUrl,
                       @Value("${app.vnpay.api-url:}") String apiUrl) {
        this.tmnCode = properties.tmnCode();
        this.hashSecret = properties.hashSecret();
        this.payUrl = properties.payUrl();
        this.returnUrl = resolveReturnUrl(properties.returnUrl(), frontendBaseUrl);
        this.apiUrl = resolveApiUrl(apiUrl, properties.payUrl());
        this.restClient = RestClient.builder().requestFactory(timeouts()).build();
        // Bean singleton: cảnh báo đúng 1 lần lúc khởi động, không chặn app chạy
        if (!properties.isConfigured()) {
            log.warn("T1: VNPay chưa cấu hình (app.vnpay.tmn-code / app.vnpay.hash-secret trống). "
                    + "App vẫn chạy nhưng mọi lần tạo thanh toán sẽ trả SERVICE_UNAVAILABLE.");
        }
    }

    public VnpayClient(VnpayProperties properties, String frontendBaseUrl) {
        this(properties, frontendBaseUrl, "");
    }

    public VnpayClient(VnpayProperties properties) {
        this(properties, "http://localhost:5173");
    }

    // Chỉ dùng cho querydr (job đối soát gọi khi đang giữ khóa dòng của đơn), nên để ngắn: tổng tối đa ~15 giây
    private static SimpleClientHttpRequestFactory timeouts() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return factory;
    }

    /** API truy vấn (querydr) nằm ở host khác cổng thanh toán; cổng sandbox -> API sandbox, còn lại -> API production. */
    static String resolveApiUrl(String explicitApiUrl, String payUrl) {
        if (explicitApiUrl != null && !explicitApiUrl.isBlank()) {
            return explicitApiUrl.trim();
        }
        return payUrl != null && payUrl.contains("sandbox.vnpayment.vn") ? SANDBOX_API_URL : PRODUCTION_API_URL;
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
        return buildPaymentUrl(txnId, amountVnd, orderInfo, ipAddress, null);
    }

    /**
     * @param createdAt giờ tạo đơn (giờ máy chủ). querydr phải gửi lại đúng vnp_CreateDate này, nên truyền
     *                  {@code PaymentTransaction.createdAt} để sau này dựng lại được; null thì lấy giờ hiện tại.
     */
    public String buildPaymentUrl(UUID txnId, long amountVnd, String orderInfo, String ipAddress, LocalDateTime createdAt) {
        ensureConfigured();

        ZonedDateTime now = createdAt == null ? ZonedDateTime.now(VN_ZONE) : toVnTime(createdAt);
        ZonedDateTime expire = now.plusMinutes(PAYMENT_URL_TTL_MINUTES);

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

    static ZonedDateTime toVnTime(LocalDateTime serverLocal) {
        return serverLocal.atZone(ZoneId.systemDefault()).withZoneSameInstant(VN_ZONE);
    }

    /**
     * Kết quả querydr. {@code signatureValid=false} thì chỉ được dùng để coi đơn là chưa thanh toán, không bao giờ
     * dùng để kích hoạt gói. {@code reachable=false}: lỗi mạng / VNPay không trả lời, chưa biết gì về đơn.
     */
    public record QueryResult(boolean reachable, boolean signatureValid, String responseCode, String message,
                              String transactionStatus, long amountVnd, String transactionNo, String bankCode) {

        static QueryResult unreachable(String reason) {
            return new QueryResult(false, false, null, reason, null, -1L, null, null);
        }

        /** VNPay xác nhận đã trừ tiền thành công. */
        public boolean paid() {
            return reachable && signatureValid && "00".equals(responseCode) && "00".equals(transactionStatus);
        }

        /** VNPay không có giao dịch nào cho mã đơn này: người dùng chưa từng thanh toán. */
        public boolean notFound() {
            return reachable && "91".equals(responseCode);
        }
    }

    /**
     * Truy vấn trạng thái giao dịch (vnp_Command=querydr) để đối soát đơn PENDING khi IPN không về.
     * Không ném lỗi: mọi lỗi mạng hoặc phản hồi lạ trả về {@link QueryResult#unreachable}.
     */
    public QueryResult queryTransaction(UUID txnId, LocalDateTime createdAt, String orderInfo, String ipAddress) {
        ensureConfigured();
        ZonedDateTime now = ZonedDateTime.now(VN_ZONE);
        String requestId = UUID.randomUUID().toString().replace("-", "");
        String txnRef = toTxnRef(txnId);
        String transactionDate = toVnTime(createdAt).format(VNP_TIME_FMT);
        String createDate = now.format(VNP_TIME_FMT);
        String ip = normalizeIpv4(ipAddress);
        String info = sanitizeOrderInfo(orderInfo, txnId);

        Map<String, String> body = new LinkedHashMap<>();
        body.put("vnp_RequestId", requestId);
        body.put("vnp_Version", "2.1.0");
        body.put("vnp_Command", "querydr");
        body.put("vnp_TmnCode", tmnCode);
        body.put("vnp_TxnRef", txnRef);
        body.put("vnp_OrderInfo", info);
        body.put("vnp_TransactionDate", transactionDate);
        body.put("vnp_CreateDate", createDate);
        body.put("vnp_IpAddr", ip);
        body.put("vnp_SecureHash", hmacSha512(hashSecret, String.join("|",
                requestId, "2.1.0", "querydr", tmnCode, txnRef, transactionDate, createDate, ip, info)));

        Map<String, Object> response;
        try {
            response = postQuery(body);
        } catch (RuntimeException ex) {
            log.warn("VNPay querydr for {} failed: {}", txnRef, ex.getMessage());
            return QueryResult.unreachable(ex.getMessage());
        }
        if (response == null || response.isEmpty()) {
            return QueryResult.unreachable("empty response");
        }
        return toQueryResult(response);
    }

    /** Tách riêng để test thay bằng phản hồi giả, không gọi mạng. */
    @SuppressWarnings("unchecked")
    Map<String, Object> postQuery(Map<String, String> body) {
        return restClient.post().uri(apiUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);
    }

    QueryResult toQueryResult(Map<String, Object> r) {
        String responseCode = str(r, "vnp_ResponseCode");
        String hashData = String.join("|",
                str(r, "vnp_ResponseId"), str(r, "vnp_Command"), responseCode, str(r, "vnp_Message"),
                str(r, "vnp_TmnCode"), str(r, "vnp_TxnRef"), str(r, "vnp_Amount"), str(r, "vnp_BankCode"),
                str(r, "vnp_PayDate"), str(r, "vnp_TransactionNo"), str(r, "vnp_TransactionType"),
                str(r, "vnp_TransactionStatus"), str(r, "vnp_OrderInfo"), str(r, "vnp_PromotionCode"),
                str(r, "vnp_PromotionAmount"));
        String received = str(r, "vnp_SecureHash");
        boolean signatureValid = !received.isBlank() && MessageDigest.isEqual(
                hmacSha512(hashSecret, hashData).getBytes(StandardCharsets.UTF_8),
                received.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
        long amountVnd = -1L;
        try {
            long raw = Long.parseLong(str(r, "vnp_Amount").trim());
            amountVnd = raw % 100L == 0 ? raw / 100L : -1L;
        } catch (NumberFormatException ignored) {
            // để -1: không khớp số tiền nào
        }
        return new QueryResult(true, signatureValid, responseCode, str(r, "vnp_Message"),
                str(r, "vnp_TransactionStatus"), amountVnd, str(r, "vnp_TransactionNo"), str(r, "vnp_BankCode"));
    }

    private static String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? "" : String.valueOf(v);
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
