package com.ltld.app.legacyvault.service.paymentservice;

import com.ltld.app.legacyvault.dto.paymentdto.CheckoutRequest;
import com.ltld.app.legacyvault.dto.paymentdto.CheckoutResponse;
import com.ltld.app.legacyvault.dto.paymentdto.PaymentTransactionResponse;
import com.ltld.app.legacyvault.dto.paymentdto.SubscriptionMeResponse;
import com.ltld.app.legacyvault.dto.paymentdto.SubscriptionPlanDto;
import com.ltld.app.legacyvault.entity.PaymentTransaction;
import com.ltld.app.legacyvault.entity.Subscription;
import com.ltld.app.legacyvault.entity.SubscriptionPlan;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.PaymentProvider;
import com.ltld.app.legacyvault.enums.PaymentStatus;
import com.ltld.app.legacyvault.enums.SubscriptionStatus;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.repository.PaymentTransactionRepository;
import com.ltld.app.legacyvault.repository.SubscriptionPlanRepository;
import com.ltld.app.legacyvault.repository.SubscriptionRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.NoTransactionException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * T1: Xử lý thanh toán VNPay, IPN idempotent, kích hoạt/gia hạn gói VIP.
 *
 * <p>Nguồn sự thật duy nhất để kích hoạt gói là IPN (server-to-server). Return URL chỉ kiểm chữ ký rồi chuyển
 * người dùng về frontend; chỉ khi {@code app.vnpay.activate-on-return=true} (dev local, VNPay không gọi được IPN
 * vào localhost) Return mới dùng chung logic kích hoạt với IPN.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final String RSP_CODE = "RspCode";
    private static final String RSP_MESSAGE = "Message";

    private final UserRepository userRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final EntitlementServiceImpl entitlementService;
    private final VnpayClient vnpayClient;
    private final VnpayProperties vnpayProperties;
    private final PaypalClient paypalClient;
    private final AuditLogService auditLogService;

    @Value("${app.frontend.base-url}")
    private String frontendBaseUrl = "http://localhost:5173";

    @Override
    @Transactional
    public CheckoutResponse checkout(UUID userId, CheckoutRequest request, String ipAddress) {
        PaymentProvider provider = parseProvider(request.getProvider());
        if (provider == PaymentProvider.PAYPAL) {
            paypalClient.ensureConfigured();
        } else {
            vnpayClient.ensureConfigured();
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "User not found."));

        String code = request.getPlanCode().trim().toUpperCase();
        SubscriptionPlan plan = subscriptionPlanRepository.findByCode(code)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "Subscription plan not found: " + code));

        if (plan.getPriceVnd() <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Cannot checkout a free plan.");
        }

        PaymentTransaction tx = PaymentTransaction.builder()
                .user(user)
                .plan(plan)
                .amountVnd(plan.getPriceVnd())
                .status(PaymentStatus.PENDING)
                .provider(provider)
                .build();
        tx = paymentTransactionRepository.save(tx);

        String paymentUrl;
        if (provider == PaymentProvider.PAYPAL) {
            long usdCents = paypalClient.toUsdCents(plan.getPriceVnd());
            PaypalClient.Order order = paypalClient.createOrder(tx.getId(), usdCents, "LegacyVault " + plan.getCode());
            tx.setAmountUsdCents(usdCents);
            tx.setGatewayTxnRef(order.id());
            paymentTransactionRepository.save(tx);
            paymentUrl = order.approveUrl();
        } else {
            String orderInfo = "Nang cap LegacyVault " + plan.getCode() + " ma " + VnpayClient.toTxnRef(tx.getId());
            paymentUrl = vnpayClient.buildPaymentUrl(tx.getId(), plan.getPriceVnd(), orderInfo, ipAddress);
        }

        return CheckoutResponse.builder()
                .transactionId(tx.getId())
                .paymentUrl(paymentUrl)
                .planCode(plan.getCode())
                .amountVnd(plan.getPriceVnd())
                .build();
    }

    /**
     * IPN không bao giờ ném lỗi nghiệp vụ: luôn trả {RspCode, Message} (HTTP 200) theo đặc tả VNPay.
     * Lỗi bất ngờ (vd. DB) làm transaction rollback và để controller trả RspCode 99.
     */
    @Override
    @Transactional
    public Map<String, String> handleVnpayIpn(Map<String, String> params) {
        return processPayment(params);
    }

    @Override
    @Transactional
    public String handleVnpayReturn(Map<String, String> params) {
        String txnRef = params.getOrDefault("vnp_TxnRef", "");
        if (!vnpayClient.verify(params)) {
            return buildFrontendResultUrl("failed", txnRef, ErrorCode.PAYMENT_SIGNATURE_INVALID.name());
        }

        boolean paidPerGateway = isPaid(params);
        boolean success = paidPerGateway;
        // Dev local: cho phép Return URL kích hoạt gói (app.vnpay.activate-on-return, mặc định false)
        if (vnpayProperties.activateOnReturn()) {
            try {
                String rspCode = processPayment(params).get(RSP_CODE);
                success = paidPerGateway && ("00".equals(rspCode) || "02".equals(rspCode));
            } catch (RuntimeException ex) {
                log.warn("T1: activate-on-return failed for txnRef {}: {}", txnRef, ex.getMessage());
                // n6: không để đơn đã chuyển SUCCESS được commit khi bước tạo gói lỗi; IPN sau đó vẫn xử lý lại được đơn PENDING
                markRollbackOnly();
                success = false;
            }
        }
        return buildFrontendResultUrl(success ? "success" : "failed", txnRef, null);
    }

    private static void markRollbackOnly() {
        try {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        } catch (NoTransactionException ignored) {
            // Gọi ngoài transaction (unit test với mock): không có gì để rollback
        }
    }

    /** Logic dùng chung cho IPN và (khi bật cờ dev) Return URL. */
    private Map<String, String> processPayment(Map<String, String> params) {
        if (!vnpayClient.verify(params)) {
            return response("97", "Invalid Checksum");
        }

        UUID txId = parseTxnRef(params.get("vnp_TxnRef"));
        if (txId == null) {
            return response("01", "Order not found");
        }
        PaymentTransaction tx = paymentTransactionRepository.findByIdForUpdate(txId).orElse(null);
        if (tx == null) {
            return response("01", "Order not found");
        }

        // Idempotency: đơn đã xử lý (SUCCESS hoặc FAILED) thì không xử lý lại
        if (tx.getStatus() != PaymentStatus.PENDING) {
            return response("02", "Order already confirmed");
        }

        User user = tx.getUser();
        long vnpAmountVnd = parseVnpAmountVnd(params.get("vnp_Amount"));
        if (vnpAmountVnd != tx.getAmountVnd()) {
            tx.setStatus(PaymentStatus.FAILED);
            tx.setVnpResponseCode(params.getOrDefault("vnp_ResponseCode", "04"));
            paymentTransactionRepository.save(tx);
            logFailure(tx, user, "Amount mismatch: expected " + tx.getAmountVnd() + " VND, got " + vnpAmountVnd + " VND");
            return response("04", "Invalid amount");
        }

        String responseCode = params.getOrDefault("vnp_ResponseCode", "");
        tx.setVnpResponseCode(responseCode);
        tx.setVnpTransactionNo(params.get("vnp_TransactionNo"));
        tx.setVnpBankCode(params.get("vnp_BankCode"));

        if (isPaid(params)) {
            LocalDateTime now = LocalDateTime.now();
            tx.setStatus(PaymentStatus.SUCCESS);
            tx.setPaidAt(now);
            paymentTransactionRepository.save(tx);

            SubscriptionPlan plan = tx.getPlan();
            activateOrExtendSubscription(user, plan, now);

            auditLogService.log(
                    AuditAction.PAYMENT_SUCCEEDED,
                    AuditResult.SUCCESS,
                    user != null ? user.getId() : null,
                    user != null ? user.getEmail() : null,
                    "PaymentTransaction",
                    tx.getId().toString(),
                    plan != null ? plan.getCode() : "VIP"
            );
        } else {
            tx.setStatus(PaymentStatus.FAILED);
            paymentTransactionRepository.save(tx);
            logFailure(tx, user, "vnp_ResponseCode=" + responseCode
                    + ", vnp_TransactionStatus=" + params.getOrDefault("vnp_TransactionStatus", ""));
        }
        // Đã ghi nhận kết quả từ VNPay (kể cả giao dịch thất bại): trả 00 để VNPay ngừng gọi lại
        return response("00", "Confirm Success");
    }

    /** Giữ khóa dòng giao dịch trong lúc capture để hai lần quay về song song không gia hạn gói hai lần. */
    @Override
    @Transactional
    public String handlePaypalReturn(String orderId) {
        PaymentTransaction tx = findPaypalTransactionForUpdate(orderId);
        if (tx == null) {
            return buildFrontendResultUrl("failed", null, ErrorCode.NOT_FOUND.name());
        }
        String ref = tx.getId().toString();
        if (tx.getStatus() != PaymentStatus.PENDING) {
            return buildFrontendResultUrl(tx.getStatus() == PaymentStatus.SUCCESS ? "success" : "failed", ref, null);
        }

        PaypalClient.Capture capture;
        try {
            capture = paypalClient.capture(orderId);
        } catch (RuntimeException ex) {
            // Chưa biết PayPal đã trừ tiền chưa: giữ PENDING để lần quay về sau capture lại an toàn
            log.warn("PayPal: capture of order {} failed: {}", orderId, ex.getMessage());
            return buildFrontendResultUrl("failed", ref, ErrorCode.SERVICE_UNAVAILABLE.name());
        }

        User user = tx.getUser();
        tx.setVnpResponseCode(capture.status());
        // Hóa đơn và lịch sử giao dịch đọc mã giao dịch / ngân hàng từ hai cột này
        tx.setVnpTransactionNo(capture.captureId());
        tx.setVnpBankCode(PaymentProvider.PAYPAL.name());
        if (!capture.completed()) {
            markFailed(tx, user, "PayPal capture status=" + capture.status());
            return buildFrontendResultUrl("failed", ref, null);
        }
        if (!"USD".equals(capture.currency()) || tx.getAmountUsdCents() == null
                || capture.cents() != tx.getAmountUsdCents()) {
            markFailed(tx, user, "Amount mismatch: expected " + tx.getAmountUsdCents() + " USD cents, got "
                    + capture.cents() + " " + capture.currency());
            return buildFrontendResultUrl("failed", ref, ErrorCode.PAYMENT_AMOUNT_MISMATCH.name());
        }

        LocalDateTime now = LocalDateTime.now();
        tx.setStatus(PaymentStatus.SUCCESS);
        tx.setPaidAt(now);
        paymentTransactionRepository.save(tx);
        activateOrExtendSubscription(user, tx.getPlan(), now);
        auditLogService.log(
                AuditAction.PAYMENT_SUCCEEDED,
                AuditResult.SUCCESS,
                user != null ? user.getId() : null,
                user != null ? user.getEmail() : null,
                "PaymentTransaction",
                tx.getId().toString(),
                tx.getPlan() != null ? tx.getPlan().getCode() : "VIP"
        );
        return buildFrontendResultUrl("success", ref, null);
    }

    @Override
    @Transactional
    public String handlePaypalCancel(String orderId) {
        PaymentTransaction tx = findPaypalTransactionForUpdate(orderId);
        if (tx == null) {
            return buildFrontendResultUrl("failed", null, null);
        }
        if (tx.getStatus() == PaymentStatus.PENDING) {
            tx.setVnpResponseCode("CANCELLED");
            markFailed(tx, tx.getUser(), "PayPal checkout cancelled by payer");
        }
        return buildFrontendResultUrl("failed", tx.getId().toString(), null);
    }

    @Override
    public List<String> getAvailableProviders() {
        List<String> providers = new ArrayList<>();
        if (vnpayClient.isConfigured()) {
            providers.add(PaymentProvider.VNPAY.name());
        }
        if (paypalClient.isConfigured()) {
            providers.add(PaymentProvider.PAYPAL.name());
        }
        return providers;
    }

    private PaymentTransaction findPaypalTransactionForUpdate(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return null;
        }
        return paymentTransactionRepository.findByGatewayTxnRefForUpdate(orderId)
                .filter(tx -> tx.getProvider() == PaymentProvider.PAYPAL)
                .orElse(null);
    }

    private void markFailed(PaymentTransaction tx, User user, String detail) {
        tx.setStatus(PaymentStatus.FAILED);
        paymentTransactionRepository.save(tx);
        logFailure(tx, user, detail);
    }

    private static PaymentProvider parseProvider(String raw) {
        if (raw == null || raw.isBlank()) {
            return PaymentProvider.VNPAY;
        }
        try {
            return PaymentProvider.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Unsupported payment provider: " + raw);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public SubscriptionMeResponse getMySubscription(UUID userId) {
        List<SubscriptionPlanDto> plans = subscriptionPlanRepository.findAllByOrderByPriceVndAsc().stream()
                .map(SubscriptionPlanDto::from)
                .toList();

        Optional<Subscription> activeSubOpt = subscriptionRepository
                .findFirstByUserIdAndStatusOrderByExpiresAtDesc(userId, SubscriptionStatus.ACTIVE);

        if (activeSubOpt.isPresent()) {
            Subscription sub = activeSubOpt.get();
            if (sub.getExpiresAt() == null || sub.getExpiresAt().isAfter(LocalDateTime.now())) {
                SubscriptionPlan plan = sub.getPlan();
                return SubscriptionMeResponse.builder()
                        .planCode(plan.getCode())
                        .planName(plan.getName())
                        .status(sub.getStatus().name())
                        .startedAt(sub.getStartedAt())
                        .expiresAt(sub.getExpiresAt())
                        .maxVaults(plan.getMaxVaults())
                        .maxBeneficiariesPerVault(plan.getMaxBeneficiaries())
                        .maxStorageBytes(plan.getStorageQuotaBytes())
                        .legacyMessageEnabled(plan.isLegacyMessagesEnabled())
                        .vip(plan.isVip())
                        .availablePlans(plans)
                        .build();
            }
        }

        Optional<Subscription> latestSubOpt = subscriptionRepository.findFirstByUserIdOrderByCreatedAtDesc(userId);
        SubscriptionPlan freePlan = entitlementService.resolveEffectivePlan(userId);
        String currentStatus = latestSubOpt.map(s -> s.getStatus().name()).orElse(SubscriptionStatus.ACTIVE.name());
        LocalDateTime expiresAt = latestSubOpt.map(Subscription::getExpiresAt).orElse(null);

        return SubscriptionMeResponse.builder()
                .planCode(freePlan.getCode())
                .planName(freePlan.getName())
                .status(currentStatus)
                .startedAt(latestSubOpt.map(Subscription::getStartedAt).orElse(null))
                .expiresAt(expiresAt)
                .maxVaults(freePlan.getMaxVaults())
                .maxBeneficiariesPerVault(freePlan.getMaxBeneficiaries())
                .maxStorageBytes(freePlan.getStorageQuotaBytes())
                .legacyMessageEnabled(freePlan.isLegacyMessagesEnabled())
                .vip(false)
                .availablePlans(plans)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentTransactionResponse> getMyPayments(UUID userId) {
        return paymentTransactionRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(PaymentTransactionResponse::from)
                .toList();
    }

    private void activateOrExtendSubscription(User user, SubscriptionPlan plan, LocalDateTime now) {
        if (user == null || plan == null) {
            return;
        }
        // m7: khóa dòng users của Owner trước khi đọc/ghi subscription, để hai IPN song song của cùng Owner
        // không cùng tạo hai subscription ACTIVE hoặc ghi đè hạn của nhau
        userRepository.findByIdForUpdate(user.getId());

        int durationDays = (plan.getDurationDays() != null && plan.getDurationDays() > 0)
                ? plan.getDurationDays()
                : 30;
        Optional<Subscription> activeOpt = subscriptionRepository
                .findFirstByUserIdAndStatusOrderByExpiresAtDesc(user.getId(), SubscriptionStatus.ACTIVE);

        if (activeOpt.isPresent()) {
            Subscription existing = activeOpt.get();
            LocalDateTime baseTime = (existing.getExpiresAt() != null && existing.getExpiresAt().isAfter(now))
                    ? existing.getExpiresAt()
                    : now;
            existing.setPlan(plan);
            existing.setStatus(SubscriptionStatus.ACTIVE);
            existing.setReminderSent(false);
            existing.setExpiresAt(baseTime.plusDays(durationDays));
            subscriptionRepository.save(existing);
        } else {
            Subscription created = Subscription.builder()
                    .user(user)
                    .plan(plan)
                    .status(SubscriptionStatus.ACTIVE)
                    .startedAt(now)
                    .expiresAt(now.plusDays(durationDays))
                    .reminderSent(false)
                    .build();
            subscriptionRepository.save(created);
        }
    }

    private void logFailure(PaymentTransaction tx, User user, String detail) {
        auditLogService.log(
                AuditAction.PAYMENT_FAILED,
                AuditResult.FAILURE,
                user != null ? user.getId() : null,
                user != null ? user.getEmail() : null,
                "PaymentTransaction",
                tx.getId().toString(),
                detail
        );
    }

    // VNPay chỉ coi giao dịch thành công khi CẢ HAI mã đều là "00"
    private static boolean isPaid(Map<String, String> params) {
        return "00".equals(params.get("vnp_ResponseCode")) && "00".equals(params.get("vnp_TransactionStatus"));
    }

    private static Map<String, String> response(String code, String message) {
        return Map.of(RSP_CODE, code, RSP_MESSAGE, message);
    }

    private static UUID parseTxnRef(String txnRef) {
        return VnpayClient.parseTxnRef(txnRef);
    }

    private long parseVnpAmountVnd(String rawAmount) {
        if (rawAmount == null || rawAmount.isBlank()) {
            return -1L;
        }
        try {
            long raw = Long.parseLong(rawAmount.trim());
            return raw % 100L == 0 ? raw / 100L : -1L;
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    private String buildFrontendResultUrl(String status, String txnRef, String errorCode) {
        String base = (frontendBaseUrl == null || frontendBaseUrl.isBlank())
                ? ""
                : frontendBaseUrl.replaceAll("/+$", "");
        StringBuilder sb = new StringBuilder(base)
                .append("/payment/result?status=").append(status);
        if (txnRef != null && !txnRef.isBlank()) {
            sb.append("&txnRef=").append(URLEncoder.encode(txnRef, StandardCharsets.UTF_8));
        }
        if (errorCode != null && !errorCode.isBlank()) {
            sb.append("&errorCode=").append(URLEncoder.encode(errorCode, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }
}
