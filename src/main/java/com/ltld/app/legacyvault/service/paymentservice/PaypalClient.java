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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** PayPal Orders API v2: tạo order rồi capture khi người mua quay về. Không dùng webhook nên chạy được ở local. */
@Slf4j
@Component
@EnableConfigurationProperties(PaypalProperties.class)
public class PaypalClient {

    public record Order(String id, String approveUrl) {}

    /** {@code completed=false} nghĩa là PayPal từ chối capture (lỗi 4xx hoặc trạng thái khác COMPLETED). */
    public record Capture(boolean completed, String status, String captureId, String currency, long cents) {
        static Capture declined(String status) {
            return new Capture(false, status, null, null, -1L);
        }
    }

    private record TokenResponse(String access_token) {}
    private record Link(String rel, String href) {}
    private record OrderResponse(String id, List<Link> links) {}
    private record Amount(String currency_code, String value) {}
    private record CaptureItem(String id, String status, Amount amount) {}
    private record Payments(List<CaptureItem> captures) {}
    private record PurchaseUnit(Payments payments) {}
    private record CaptureResponse(String status, List<PurchaseUnit> purchase_units) {}

    private final PaypalProperties properties;
    private final RestClient client;
    private final String returnUrl;
    private final String cancelUrl;

    @Autowired
    public PaypalClient(PaypalProperties properties, @Value("${app.frontend.base-url}") String frontendBaseUrl) {
        this(properties, frontendBaseUrl, RestClient.builder().requestFactory(timeouts()));
    }

    PaypalClient(PaypalProperties properties, String frontendBaseUrl, RestClient.Builder builder) {
        this.properties = properties;
        this.client = builder.baseUrl(properties.baseUrl()).build();
        // Chung host với VNPay nên suy ra cùng một cách, không cần thêm biến môi trường khi deploy
        String vnpayReturnUrl = VnpayClient.resolveReturnUrl("", frontendBaseUrl);
        this.returnUrl = vnpayReturnUrl.replace("/vnpay-return", "/paypal-return");
        this.cancelUrl = vnpayReturnUrl.replace("/vnpay-return", "/paypal-cancel");
        if (!properties.isConfigured()) {
            log.warn("PayPal chưa cấu hình (app.paypal.client-id / app.paypal.secret trống): cổng PayPal sẽ bị ẩn.");
        }
    }

    private static SimpleClientHttpRequestFactory timeouts() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(20));
        return factory;
    }

    public boolean isConfigured() {
        return properties.isConfigured();
    }

    public void ensureConfigured() {
        if (!isConfigured()) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "PayPal payment gateway is not configured.");
        }
    }

    /** Làm tròn tới cent gần nhất. */
    public long toUsdCents(long amountVnd) {
        long rate = properties.vndPerUsd();
        return (amountVnd * 100L + rate / 2L) / rate;
    }

    public Order createOrder(UUID transactionId, long usdCents, String description) {
        ensureConfigured();
        Map<String, Object> body = Map.of(
                "intent", "CAPTURE",
                "purchase_units", List.of(Map.of(
                        "custom_id", transactionId.toString(),
                        "description", description,
                        "amount", Map.of("currency_code", "USD", "value", formatUsd(usdCents)))),
                "payment_source", Map.of("paypal", Map.of("experience_context", Map.of(
                        "brand_name", "LegacyVault",
                        "user_action", "PAY_NOW",
                        "shipping_preference", "NO_SHIPPING",
                        "return_url", returnUrl,
                        "cancel_url", cancelUrl))));
        try {
            String token = accessToken();
            OrderResponse order = client.post().uri("/v2/checkout/orders")
                    .headers(h -> h.setBearerAuth(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(OrderResponse.class);
            String approveUrl = order == null || order.links() == null ? null : order.links().stream()
                    .filter(l -> "payer-action".equals(l.rel()) || "approve".equals(l.rel()))
                    .map(Link::href)
                    .findFirst().orElse(null);
            if (approveUrl == null) {
                throw new RestClientException("PayPal response has no approval link");
            }
            return new Order(order.id(), approveUrl);
        } catch (RestClientException ex) {
            log.error("PayPal: could not create order for transaction {}: {}", transactionId, ex.getMessage());
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "Could not create PayPal order.");
        }
    }

    /**
     * Gọi lại cùng order là an toàn: PayPal-Request-Id làm PayPal trả lại kết quả cũ thay vì trừ tiền lần nữa.
     *
     * @throws RuntimeException lỗi mạng, 5xx hoặc không lấy được token: chưa biết kết quả, không được coi là thất bại
     */
    public Capture capture(String orderId) {
        ensureConfigured();
        String token = accessToken();
        try {
            CaptureResponse response = client.post().uri("/v2/checkout/orders/{id}/capture", orderId)
                    .headers(h -> {
                        h.setBearerAuth(token);
                        h.set("PayPal-Request-Id", "capture-" + orderId);
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{}")
                    .retrieve()
                    .body(CaptureResponse.class);
            return toCapture(response);
        } catch (HttpClientErrorException ex) {
            log.warn("PayPal: capture of order {} rejected with HTTP {}", orderId, ex.getStatusCode().value());
            return Capture.declined("HTTP_" + ex.getStatusCode().value());
        }
    }

    /**
     * Order đọc từ GET /v2/checkout/orders/{id}. {@code capture} chỉ có khi order đã COMPLETED (đã capture): job đối
     * soát đọc kết quả có sẵn thay vì capture lại (capture lại một order đã COMPLETED bị PayPal trả 422).
     */
    public record OrderSnapshot(String status, Capture capture) {}

    /**
     * Trạng thái order (CREATED, APPROVED, PAYER_ACTION_REQUIRED, COMPLETED, VOIDED) và kết quả capture nếu có.
     * Order không còn tồn tại (404) trả status {@code "NOT_FOUND"}.
     *
     * @throws RuntimeException lỗi mạng hoặc 5xx: chưa biết kết quả
     */
    public OrderSnapshot getOrder(String orderId) {
        ensureConfigured();
        String token = accessToken();
        try {
            CaptureResponse response = client.get().uri("/v2/checkout/orders/{id}", orderId)
                    .headers(h -> h.setBearerAuth(token))
                    .retrieve()
                    .body(CaptureResponse.class);
            String status = response == null || response.status() == null ? "UNKNOWN" : response.status();
            return new OrderSnapshot(status, "COMPLETED".equals(status) ? toCapture(response) : null);
        } catch (HttpClientErrorException.NotFound ex) {
            return new OrderSnapshot("NOT_FOUND", null);
        }
    }

    /** Chỉ trạng thái của order; xem {@link #getOrder}. */
    public String getOrderStatus(String orderId) {
        return getOrder(orderId).status();
    }

    private static Capture toCapture(CaptureResponse response) {
        CaptureItem item = response == null || response.purchase_units() == null ? null
                : response.purchase_units().stream()
                .filter(u -> u.payments() != null && u.payments().captures() != null)
                .flatMap(u -> u.payments().captures().stream())
                .findFirst().orElse(null);
        if (item == null) {
            return Capture.declined(response == null ? "EMPTY" : response.status());
        }
        boolean completed = "COMPLETED".equals(item.status()) && "COMPLETED".equals(response.status());
        Amount amount = item.amount();
        return new Capture(completed, item.status(), item.id(),
                amount == null ? null : amount.currency_code(),
                amount == null ? -1L : parseCents(amount.value()));
    }

    // shortcut: lấy token mới cho mỗi lần gọi, đủ với lưu lượng hiện tại; cache đến expires_in nếu nhiều giao dịch
    private String accessToken() {
        TokenResponse response = client.post().uri("/v1/oauth2/token")
                .headers(h -> h.setBasicAuth(properties.clientId(), properties.secret()))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("grant_type=client_credentials")
                .retrieve()
                .body(TokenResponse.class);
        if (response == null || response.access_token() == null) {
            throw new RestClientException("PayPal returned no access token");
        }
        return response.access_token();
    }

    static String formatUsd(long cents) {
        return String.format(Locale.ROOT, "%d.%02d", cents / 100L, cents % 100L);
    }

    private static long parseCents(String value) {
        try {
            return new BigDecimal(value).movePointRight(2).longValueExact();
        } catch (ArithmeticException | NumberFormatException | NullPointerException ex) {
            return -1L;
        }
    }
}
