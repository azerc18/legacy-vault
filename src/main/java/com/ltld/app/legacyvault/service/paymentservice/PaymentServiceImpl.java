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
import java.time.Duration;
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

        // Bấm thanh toán nhiều lần: đơn cũ chưa trả tiền thành EXPIRED để lịch sử không đầy đơn treo.
        // An toàn vì IPN/return về muộn cho đơn EXPIRED vẫn được xử lý (xem isOpen).
        for (PaymentTransaction old : paymentTransactionRepository.findByUserIdAndStatus(userId, PaymentStatus.PENDING)) {
            markExpired(old, "SUPERSEDED");
        }

        PaymentTransaction tx = PaymentTransaction.builder()
                .user(user)
                .plan(plan)
                .amountVnd(plan.getPriceVnd())
                .status(PaymentStatus.PENDING)
                .provider(provider)
                .build();
        tx = paymentTransactionRepository.save(tx);
        // flush để có createdAt: vnp_CreateDate phải bằng giờ này thì querydr về sau mới tìm thấy đơn
        paymentTransactionRepository.flush();

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
            paymentUrl = vnpayClient.buildPaymentUrl(tx.getId(), plan.getPriceVnd(), orderInfo, ipAddress,
                    tx.getCreatedAt());
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
        if (!isOpen(tx)) {
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
            settleSuccess(tx, LocalDateTime.now());
        } else if (tx.getStatus() == PaymentStatus.EXPIRED) {
            // Đơn đã đóng (bỏ ngang / bị thay bằng đơn mới) mà cổng báo không thu tiền: giữ EXPIRED, không báo thất bại
            paymentTransactionRepository.save(tx);
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
        if (!isOpen(tx)) {
            return buildFrontendResultUrl(tx.getStatus() == PaymentStatus.SUCCESS ? "success" : "failed", ref, null);
        }

        PaypalOutcome outcome = capturePaypal(tx, orderId);
        return buildFrontendResultUrl(outcome.success() ? "success" : "failed", ref, outcome.errorCode());
    }

    /** Kết quả capture PayPal; {@code unknown=true}: lỗi mạng, chưa biết đã trừ tiền chưa, đơn giữ nguyên trạng thái. */
    private record PaypalOutcome(boolean success, boolean unknown, String errorCode) {
    }

    /** Capture rồi ghi kết quả vào đơn đang giữ khóa; dùng chung cho lần quay về và job đối soát. */
    private PaypalOutcome capturePaypal(PaymentTransaction tx, String orderId) {
        return capturePaypal(tx, orderId, false);
    }

    /** {@code recheckOnly}: đơn đã EXPIRED, chỉ ghi khi đã thu tiền; bị từ chối thì giữ nguyên EXPIRED. */
    private PaypalOutcome capturePaypal(PaymentTransaction tx, String orderId, boolean recheckOnly) {
        PaypalClient.Capture capture;
        try {
            capture = paypalClient.capture(orderId);
        } catch (RuntimeException ex) {
            // Chưa biết PayPal đã trừ tiền chưa: giữ nguyên để lần sau capture lại an toàn
            log.warn("PayPal: capture of order {} failed: {}", orderId, ex.getMessage());
            return new PaypalOutcome(false, true, ErrorCode.SERVICE_UNAVAILABLE.name());
        }
        return applyPaypalCapture(tx, capture, recheckOnly);
    }

    /** Ghi kết quả capture (vừa capture, hoặc đọc lại từ order đã COMPLETED) vào đơn đang giữ khóa. */
    private PaypalOutcome applyPaypalCapture(PaymentTransaction tx, PaypalClient.Capture capture, boolean recheckOnly) {
        if (!capture.completed() && recheckOnly) {
            return new PaypalOutcome(false, false, null);
        }
        User user = tx.getUser();
        tx.setVnpResponseCode(capture.status());
        // Hóa đơn và lịch sử giao dịch đọc mã giao dịch / ngân hàng từ hai cột này
        tx.setVnpTransactionNo(capture.captureId());
        tx.setVnpBankCode(PaymentProvider.PAYPAL.name());
        if (!capture.completed()) {
            markFailed(tx, user, "PayPal capture status=" + capture.status());
            return new PaypalOutcome(false, false, null);
        }
        if (!"USD".equals(capture.currency()) || tx.getAmountUsdCents() == null
                || capture.cents() != tx.getAmountUsdCents()) {
            markFailed(tx, user, "Amount mismatch: expected " + tx.getAmountUsdCents() + " USD cents, got "
                    + capture.cents() + " " + capture.currency());
            return new PaypalOutcome(false, false, ErrorCode.PAYMENT_AMOUNT_MISMATCH.name());
        }
        settleSuccess(tx, LocalDateTime.now());
        return new PaypalOutcome(true, false, null);
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

    /** Đơn đã trừ tiền: ghi SUCCESS, kích hoạt/gia hạn gói, ghi audit (audit kéo theo mail + chuông). */
    private void settleSuccess(PaymentTransaction tx, LocalDateTime now) {
        User user = tx.getUser();
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
    }

    /**
     * Đơn còn nhận kết quả từ cổng: PENDING, hoặc EXPIRED (người mua thanh toán muộn ở tab cũ, sau khi job đã
     * đóng đơn hoặc sau khi tạo đơn mới). SUCCESS/FAILED là kết quả cuối, không xử lý lại.
     */
    private static boolean isOpen(PaymentTransaction tx) {
        return tx.getStatus() == PaymentStatus.PENDING || tx.getStatus() == PaymentStatus.EXPIRED;
    }

    /** Đóng đơn chưa trả tiền; không gửi mail "thanh toán thất bại" vì người mua chỉ bỏ ngang. */
    private void markExpired(PaymentTransaction tx, String reason) {
        tx.setStatus(PaymentStatus.EXPIRED);
        if (tx.getVnpResponseCode() == null || tx.getVnpResponseCode().isBlank()) {
            tx.setVnpResponseCode(reason);
        }
        paymentTransactionRepository.save(tx);
        User user = tx.getUser();
        auditLogService.log(
                AuditAction.PAYMENT_EXPIRED,
                AuditResult.SUCCESS,
                user != null ? user.getId() : null,
                user != null ? user.getEmail() : null,
                "PaymentTransaction",
                tx.getId().toString(),
                reason
        );
    }

    /**
     * Job đối soát gọi cho từng đơn PENDING quá hạn. Hỏi cổng thanh toán trước khi đóng đơn: đã trừ tiền thì kích hoạt
     * gói (trường hợp IPN bị lỡ), chưa trả thì EXPIRED. Không hỏi được cổng thì để PENDING tới lần sau, trừ khi đơn đã
     * quá {@code giveUpAfter} (lúc đó đóng luôn; nếu sau này IPN về vẫn xử lý được vì EXPIRED còn mở).
     *
     * <p>Đơn EXPIRED chưa có {@code paidAt} (bị thay khi bấm thanh toán lại, hoặc job đã đóng) cũng được hỏi lại trong
     * {@code giveUpAfter} kể từ lúc tạo, chỉ để kích hoạt nếu cổng báo đã thu tiền; không bao giờ đóng hay báo thất
     * bại lần nữa. Nhờ vậy người đã trả đơn cũ mà IPN bị lỡ rồi bấm thanh toán lại vẫn lên VIP.
     *
     * <p>Lời gọi cổng (timeout tối đa ~30 giây) diễn ra khi đang giữ khóa dòng của MỘT đơn; job chạy một luồng nên chỉ
     * IPN của đúng đơn đó phải chờ, và VNPay tự gọi lại IPN nếu bị chậm.
     */
    @Override
    @Transactional
    public ReconcileOutcome reconcilePending(UUID transactionId, LocalDateTime now, Duration giveUpAfter) {
        PaymentTransaction tx = paymentTransactionRepository.findByIdForUpdate(transactionId).orElse(null);
        if (tx == null) {
            return ReconcileOutcome.SKIPPED;
        }
        boolean recheckOnly = tx.getStatus() == PaymentStatus.EXPIRED && tx.getPaidAt() == null;
        if (tx.getStatus() != PaymentStatus.PENDING && !recheckOnly) {
            return ReconcileOutcome.SKIPPED;
        }
        boolean giveUp = tx.getCreatedAt() == null || tx.getCreatedAt().isBefore(now.minus(giveUpAfter));
        if (recheckOnly && giveUp) {
            return ReconcileOutcome.SKIPPED;
        }
        ReconcileOutcome outcome = tx.getProvider() == PaymentProvider.PAYPAL
                ? reconcilePaypal(tx, giveUp, recheckOnly)
                : reconcileVnpay(tx, now, giveUp, recheckOnly);
        // Đơn đã đóng mà vẫn chưa thu tiền: không đổi gì
        if (recheckOnly && outcome != ReconcileOutcome.ACTIVATED && outcome != ReconcileOutcome.FAILED) {
            return ReconcileOutcome.SKIPPED;
        }
        return outcome;
    }

    /**
     * Mã vnp_TransactionStatus VNPay còn đang xử lý hoặc chưa chắc chắn (01 chưa hoàn tất, 04 giao dịch đảo: đã trừ
     * tiền ở ngân hàng nhưng chưa thành công ở VNPay, 05/06 đang xử lý, 07 nghi ngờ gian lận): chờ tới giveUpAfter.
     */
    private static final java.util.Set<String> VNPAY_IN_PROGRESS = java.util.Set.of("01", "04", "05", "06", "07");

    private ReconcileOutcome reconcileVnpay(PaymentTransaction tx, LocalDateTime now, boolean giveUp,
                                            boolean recheckOnly) {
        if (!vnpayClient.isConfigured()) {
            return expireOrWait(tx, giveUp, "TIMEOUT", recheckOnly);
        }
        LocalDateTime createdAt = tx.getCreatedAt() != null ? tx.getCreatedAt() : now;
        VnpayClient.QueryResult q = vnpayClient.queryTransaction(tx.getId(), createdAt,
                "Doi soat LegacyVault " + VnpayClient.toTxnRef(tx.getId()), "127.0.0.1");

        if (q.paid()) {
            if (q.amountVnd() != tx.getAmountVnd()) {
                tx.setVnpResponseCode("04");
                markFailed(tx, tx.getUser(), "querydr amount mismatch: expected " + tx.getAmountVnd()
                        + " VND, got " + q.amountVnd() + " VND");
                return ReconcileOutcome.FAILED;
            }
            tx.setVnpResponseCode("00");
            tx.setVnpTransactionNo(q.transactionNo());
            tx.setVnpBankCode(q.bankCode());
            settleSuccess(tx, now);
            log.info("Reconcile: VNPay order {} was paid but IPN was missed; plan activated", tx.getId());
            return ReconcileOutcome.ACTIVATED;
        }
        if (recheckOnly) {
            return ReconcileOutcome.SKIPPED;
        }
        if (!q.reachable()) {
            return expireOrWait(tx, giveUp, "TIMEOUT", false);
        }
        if (q.notFound()) {
            markExpired(tx, "TIMEOUT");
            return ReconcileOutcome.EXPIRED;
        }
        // VNPay trả lời có giao dịch nhưng chưa thu tiền. Phản hồi sai chữ ký vẫn chỉ được dẫn tới EXPIRED (không
        // kích hoạt gói). Mã còn đang xử lý thì chờ thêm, vì tiền có thể vẫn về.
        if ("00".equals(q.responseCode()) && q.signatureValid()) {
            if (VNPAY_IN_PROGRESS.contains(q.transactionStatus())) {
                return expireOrWait(tx, giveUp, "TIMEOUT_" + q.transactionStatus(), false);
            }
            markExpired(tx, "TIMEOUT_" + q.transactionStatus());
            return ReconcileOutcome.EXPIRED;
        }
        log.warn("Reconcile: VNPay querydr for {} answered {} {}", tx.getId(), q.responseCode(), q.message());
        return expireOrWait(tx, giveUp, "TIMEOUT", false);
    }

    private ReconcileOutcome reconcilePaypal(PaymentTransaction tx, boolean giveUp, boolean recheckOnly) {
        String orderId = tx.getGatewayTxnRef();
        if (orderId == null || orderId.isBlank()) {
            // Không tạo được order bên PayPal: người mua chưa từng thấy trang thanh toán
            return recheckOnly ? ReconcileOutcome.SKIPPED : expire(tx, "TIMEOUT");
        }
        if (!paypalClient.isConfigured()) {
            return expireOrWait(tx, giveUp, "TIMEOUT", recheckOnly);
        }
        PaypalClient.OrderSnapshot order;
        try {
            order = paypalClient.getOrder(orderId);
        } catch (RuntimeException ex) {
            log.warn("Reconcile: PayPal order {} lookup failed: {}", orderId, ex.getMessage());
            return expireOrWait(tx, giveUp, "TIMEOUT", recheckOnly);
        }
        PaypalOutcome outcome;
        if ("COMPLETED".equals(order.status()) && order.capture() != null) {
            // Đã capture bên PayPal nhưng DB chưa ghi: đọc kết quả có sẵn, không capture lại
            outcome = applyPaypalCapture(tx, order.capture(), recheckOnly);
        } else if ("APPROVED".equals(order.status()) && !recheckOnly) {
            // Người mua đã đồng ý trả nhưng không quay về web (đóng tab): capture giống lần quay về
            outcome = capturePaypal(tx, orderId, false);
        } else if ("APPROVED".equals(order.status())) {
            // Review #251 M2: đơn đã bị thay thế (người mua đã trả đơn khác). Với PayPal, capture chính là thu tiền,
            // nên job không bao giờ capture đơn EXPIRED; chỉ người mua tự quay lại tab cũ mới capture được.
            return ReconcileOutcome.SKIPPED;
        } else {
            // CREATED / PAYER_ACTION_REQUIRED / VOIDED / NOT_FOUND: chưa trả tiền
            return recheckOnly ? ReconcileOutcome.SKIPPED : expire(tx, "TIMEOUT");
        }
        if (outcome.success()) {
            return ReconcileOutcome.ACTIVATED;
        }
        if (outcome.unknown()) {
            return expireOrWait(tx, giveUp, "TIMEOUT", recheckOnly);
        }
        return recheckOnly && tx.getStatus() == PaymentStatus.EXPIRED ? ReconcileOutcome.SKIPPED : ReconcileOutcome.FAILED;
    }

    private ReconcileOutcome expire(PaymentTransaction tx, String reason) {
        markExpired(tx, reason);
        return ReconcileOutcome.EXPIRED;
    }

    private ReconcileOutcome expireOrWait(PaymentTransaction tx, boolean giveUp, String reason, boolean recheckOnly) {
        if (recheckOnly) {
            return ReconcileOutcome.SKIPPED;
        }
        return giveUp ? expire(tx, reason) : ReconcileOutcome.STILL_PENDING;
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
                        .cancelAtPeriodEnd(sub.isCancelAtPeriodEnd())
                        .cancelledAt(sub.getCancelledAt())
                        .renewPlanCode(plan.isVip() ? plan.getCode() : null)
                        .lastProvider(lastProvider(userId))
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
                .cancelAtPeriodEnd(latestSubOpt.map(Subscription::isCancelAtPeriodEnd).orElse(false))
                .cancelledAt(latestSubOpt.map(Subscription::getCancelledAt).orElse(null))
                .renewPlanCode(latestSubOpt.map(Subscription::getPlan)
                        .filter(SubscriptionPlan::isVip).map(SubscriptionPlan::getCode).orElse(null))
                .lastProvider(lastProvider(userId))
                .availablePlans(plans)
                .build();
    }

    private String lastProvider(UUID userId) {
        return paymentTransactionRepository.findFirstByUserIdAndStatusOrderByPaidAtDesc(userId, PaymentStatus.SUCCESS)
                .map(PaymentTransaction::getProvider)
                .map(Enum::name)
                .orElse(null);
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
            // Việc 6: trả tiền gia hạn nghĩa là muốn dùng tiếp, nên bỏ cờ hủy cuối kỳ
            existing.setCancelAtPeriodEnd(false);
            existing.setCancelledAt(null);
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
