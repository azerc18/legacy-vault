package com.ltld.app.legacyvault.service.paymentservice;

import com.ltld.app.legacyvault.dto.paymentdto.CheckoutRequest;
import com.ltld.app.legacyvault.entity.PaymentTransaction;
import com.ltld.app.legacyvault.entity.Subscription;
import com.ltld.app.legacyvault.entity.SubscriptionPlan;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.PaymentProvider;
import com.ltld.app.legacyvault.enums.PaymentStatus;
import com.ltld.app.legacyvault.repository.PaymentTransactionRepository;
import com.ltld.app.legacyvault.repository.SubscriptionPlanRepository;
import com.ltld.app.legacyvault.repository.SubscriptionRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Việc 1: đóng đơn PENDING quá hạn, đối soát VNPay querydr / PayPal trước khi đóng, IPN muộn vẫn được nhận. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PendingPaymentReconcileTest {

    private static final String SECRET = "SECRET_KEY_FOR_TESTING_1234567890";
    private static final Duration GIVE_UP = Duration.ofHours(24);

    @Mock private UserRepository userRepository;
    @Mock private SubscriptionPlanRepository planRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private PaymentTransactionRepository transactionRepository;
    @Mock private EntitlementServiceImpl entitlementService;
    @Mock private PaypalClient paypalClient;
    @Mock private AuditLogService auditLogService;

    private VnpayClient vnpayClient;
    private PaymentServiceImpl service;
    private User owner;
    private SubscriptionPlan plan;
    private PaymentTransaction tx;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        VnpayProperties props = new VnpayProperties("DEMO_TMN", SECRET,
                "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html", "http://localhost:8084/api/payments/vnpay-return", false);
        vnpayClient = spy(new VnpayClient(props, "http://localhost:5173"));
        service = new PaymentServiceImpl(userRepository, planRepository, subscriptionRepository, transactionRepository,
                entitlementService, vnpayClient, props, paypalClient, auditLogService, mock(CouponService.class));
        now = LocalDateTime.of(2026, 11, 11, 12, 0);
        owner = User.builder().id(UUID.randomUUID()).email("owner@example.com").fullName("Owner").build();
        plan = SubscriptionPlan.builder().id(UUID.randomUUID()).code("VIP_MONTH").name("VIP Monthly")
                .priceVnd(99_000L).durationDays(30).build();
        tx = PaymentTransaction.builder()
                .id(UUID.randomUUID()).user(owner).plan(plan).amountVnd(99_000L)
                .status(PaymentStatus.PENDING).provider(PaymentProvider.VNPAY)
                .createdAt(now.minusMinutes(45))
                .build();
        when(transactionRepository.findByIdForUpdate(tx.getId())).thenReturn(Optional.of(tx));
        when(subscriptionRepository.findFirstByUserIdAndStatusOrderByExpiresAtDesc(any(), any()))
                .thenReturn(Optional.empty());
    }

    /** Phản hồi querydr có chữ ký đúng theo thứ tự trường của VNPay. */
    private Map<String, Object> queryResponse(String responseCode, String transactionStatus, long amountVnd) {
        Map<String, Object> r = new HashMap<>();
        r.put("vnp_ResponseId", "r1");
        r.put("vnp_Command", "querydr");
        r.put("vnp_ResponseCode", responseCode);
        r.put("vnp_Message", "QueryDR Success");
        r.put("vnp_TmnCode", "DEMO_TMN");
        r.put("vnp_TxnRef", VnpayClient.toTxnRef(tx.getId()));
        r.put("vnp_Amount", String.valueOf(amountVnd * 100));
        r.put("vnp_BankCode", "NCB");
        r.put("vnp_PayDate", "20261111113000");
        r.put("vnp_TransactionNo", "14000001");
        r.put("vnp_TransactionType", "01");
        r.put("vnp_TransactionStatus", transactionStatus);
        r.put("vnp_OrderInfo", "Doi soat");
        String data = String.join("|", "r1", "querydr", responseCode, "QueryDR Success", "DEMO_TMN",
                VnpayClient.toTxnRef(tx.getId()), String.valueOf(amountVnd * 100), "NCB", "20261111113000",
                "14000001", "01", transactionStatus, "Doi soat", "", "");
        r.put("vnp_SecureHash", VnpayClient.hmacSha512(SECRET, data));
        return r;
    }

    @Test
    void vnpay_paidButIpnMissed_activatesPlan() {
        doReturn(queryResponse("00", "00", 99_000L)).when(vnpayClient).postQuery(any());

        ReconcileOutcome outcome = service.reconcilePending(tx.getId(), now, GIVE_UP);

        assertThat(outcome).isEqualTo(ReconcileOutcome.ACTIVATED);
        assertThat(tx.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(tx.getVnpTransactionNo()).isEqualTo("14000001");
        assertThat(tx.getVnpBankCode()).isEqualTo("NCB");
        verify(subscriptionRepository).save(any(Subscription.class));
        verify(auditLogService).log(eq(AuditAction.PAYMENT_SUCCEEDED), any(), any(), any(), any(), any(), any());
    }

    @Test
    void vnpay_paidWithForgedSignature_neverActivates() {
        Map<String, Object> forged = queryResponse("00", "00", 99_000L);
        forged.put("vnp_SecureHash", "deadbeef");
        doReturn(forged).when(vnpayClient).postQuery(any());

        ReconcileOutcome outcome = service.reconcilePending(tx.getId(), now, GIVE_UP);

        assertThat(outcome).isEqualTo(ReconcileOutcome.STILL_PENDING);
        assertThat(tx.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void vnpay_paidWithWrongAmount_marksFailed() {
        doReturn(queryResponse("00", "00", 1_000L)).when(vnpayClient).postQuery(any());

        assertThat(service.reconcilePending(tx.getId(), now, GIVE_UP)).isEqualTo(ReconcileOutcome.FAILED);
        assertThat(tx.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void vnpay_notFound_expiresWithoutFailureMail() {
        doReturn(queryResponse("91", "", 0L)).when(vnpayClient).postQuery(any());

        assertThat(service.reconcilePending(tx.getId(), now, GIVE_UP)).isEqualTo(ReconcileOutcome.EXPIRED);
        assertThat(tx.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(tx.getVnpResponseCode()).isEqualTo("TIMEOUT");
        verify(auditLogService).log(eq(AuditAction.PAYMENT_EXPIRED), any(), any(), any(), any(), any(), any());
        verify(auditLogService, never()).log(eq(AuditAction.PAYMENT_FAILED), any(), any(), any(), any(), any(), any());
    }

    @Test
    void vnpay_failedAtGateway_expires() {
        doReturn(queryResponse("00", "02", 99_000L)).when(vnpayClient).postQuery(any());

        assertThat(service.reconcilePending(tx.getId(), now, GIVE_UP)).isEqualTo(ReconcileOutcome.EXPIRED);
        assertThat(tx.getVnpResponseCode()).isEqualTo("TIMEOUT_02");
    }

    // Review #251 m3: 01/04/05/06/07 là còn đang xử lý, tiền có thể vẫn về -> chờ tới giveUpAfter mới đóng
    @Test
    void vnpay_stillProcessing_waitsUntilGiveUp() {
        for (String status : List.of("01", "04", "05", "06", "07")) {
            tx.setStatus(PaymentStatus.PENDING);
            tx.setVnpResponseCode(null);
            tx.setCreatedAt(now.minusMinutes(45));
            doReturn(queryResponse("00", status, 99_000L)).when(vnpayClient).postQuery(any());

            assertThat(service.reconcilePending(tx.getId(), now, GIVE_UP)).isEqualTo(ReconcileOutcome.STILL_PENDING);
            assertThat(tx.getStatus()).isEqualTo(PaymentStatus.PENDING);

            tx.setCreatedAt(now.minusHours(25));
            assertThat(service.reconcilePending(tx.getId(), now, GIVE_UP)).isEqualTo(ReconcileOutcome.EXPIRED);
            assertThat(tx.getVnpResponseCode()).isEqualTo("TIMEOUT_" + status);
        }
    }

    // Review #251 M1: trả đơn 1, IPN lỡ, bấm thanh toán lại -> đơn 1 thành EXPIRED (SUPERSEDED). Job vẫn hỏi lại
    // VNPay cho đơn 1 và kích hoạt gói.
    @Test
    void vnpay_supersededOrderPaidButIpnMissed_isRecheckedAndActivates() {
        tx.setStatus(PaymentStatus.EXPIRED);
        tx.setVnpResponseCode("SUPERSEDED");
        tx.setCreatedAt(now.minusMinutes(5));
        doReturn(queryResponse("00", "00", 99_000L)).when(vnpayClient).postQuery(any());

        assertThat(service.reconcilePending(tx.getId(), now, GIVE_UP)).isEqualTo(ReconcileOutcome.ACTIVATED);
        assertThat(tx.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(subscriptionRepository).save(any(Subscription.class));
    }

    @Test
    void vnpay_recheckOfExpiredOrder_notPaid_changesNothing() {
        tx.setStatus(PaymentStatus.EXPIRED);
        tx.setVnpResponseCode("SUPERSEDED");
        for (Map<String, Object> answer : List.of(queryResponse("91", "", 0L), queryResponse("00", "02", 99_000L))) {
            doReturn(answer).when(vnpayClient).postQuery(any());
            assertThat(service.reconcilePending(tx.getId(), now, GIVE_UP)).isEqualTo(ReconcileOutcome.SKIPPED);
        }
        doThrow(new RuntimeException("timeout")).when(vnpayClient).postQuery(any());
        assertThat(service.reconcilePending(tx.getId(), now, GIVE_UP)).isEqualTo(ReconcileOutcome.SKIPPED);

        assertThat(tx.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(tx.getVnpResponseCode()).isEqualTo("SUPERSEDED");
        verify(auditLogService, never()).log(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void vnpay_expiredOrderOlderThanGiveUp_isNotQueriedAgain() {
        tx.setStatus(PaymentStatus.EXPIRED);
        tx.setCreatedAt(now.minusHours(25));

        assertThat(service.reconcilePending(tx.getId(), now, GIVE_UP)).isEqualTo(ReconcileOutcome.SKIPPED);
        verify(vnpayClient, never()).postQuery(any());
    }

    // Review #251 n1: IPN báo thất bại cho đơn đã EXPIRED thì giữ EXPIRED, không ghi PAYMENT_FAILED
    @Test
    void vnpay_failureIpnForExpiredOrder_staysExpired() {
        tx.setStatus(PaymentStatus.EXPIRED);
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", VnpayClient.toTxnRef(tx.getId()));
        params.put("vnp_Amount", "9900000");
        params.put("vnp_ResponseCode", "24");
        params.put("vnp_TransactionStatus", "02");
        params.put("vnp_SecureHash", vnpayClient.signParams(params));

        assertThat(service.handleVnpayIpn(params).get("RspCode")).isEqualTo("00");
        assertThat(tx.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        verify(auditLogService, never()).log(eq(AuditAction.PAYMENT_FAILED), any(), any(), any(), any(), any(), any());
    }

    @Test
    void vnpay_unreachable_waitsThenGivesUpAfterADay() {
        doThrow(new RuntimeException("connect timed out")).when(vnpayClient).postQuery(any());

        assertThat(service.reconcilePending(tx.getId(), now, GIVE_UP)).isEqualTo(ReconcileOutcome.STILL_PENDING);
        assertThat(tx.getStatus()).isEqualTo(PaymentStatus.PENDING);

        tx.setCreatedAt(now.minusHours(25));
        assertThat(service.reconcilePending(tx.getId(), now, GIVE_UP)).isEqualTo(ReconcileOutcome.EXPIRED);
    }

    @Test
    void vnpay_lateIpnForExpiredOrder_stillActivates() {
        tx.setStatus(PaymentStatus.EXPIRED);
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", VnpayClient.toTxnRef(tx.getId()));
        params.put("vnp_Amount", "9900000");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_SecureHash", vnpayClient.signParams(params));

        Map<String, String> resp = service.handleVnpayIpn(params);

        assertThat(resp.get("RspCode")).isEqualTo("00");
        assertThat(tx.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(subscriptionRepository).save(any(Subscription.class));
    }

    @Test
    void alreadySettledByIpn_isSkipped() {
        tx.setStatus(PaymentStatus.SUCCESS);

        assertThat(service.reconcilePending(tx.getId(), now, GIVE_UP)).isEqualTo(ReconcileOutcome.SKIPPED);
        verify(vnpayClient, never()).postQuery(any());
    }

    @Test
    void paypal_approvedButBuyerClosedTab_capturesAndActivates() {
        tx.setProvider(PaymentProvider.PAYPAL);
        tx.setGatewayTxnRef("ORDER1");
        tx.setAmountUsdCents(390L);
        when(paypalClient.isConfigured()).thenReturn(true);
        when(paypalClient.getOrder("ORDER1")).thenReturn(new PaypalClient.OrderSnapshot("APPROVED", null));
        when(paypalClient.capture("ORDER1")).thenReturn(new PaypalClient.Capture(true, "COMPLETED", "CAP1", "USD", 390L));

        assertThat(service.reconcilePending(tx.getId(), now, GIVE_UP)).isEqualTo(ReconcileOutcome.ACTIVATED);
        assertThat(tx.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    void paypal_neverApproved_expiresWithoutCapture() {
        tx.setProvider(PaymentProvider.PAYPAL);
        tx.setGatewayTxnRef("ORDER1");
        when(paypalClient.isConfigured()).thenReturn(true);
        when(paypalClient.getOrder("ORDER1")).thenReturn(new PaypalClient.OrderSnapshot("PAYER_ACTION_REQUIRED", null));

        assertThat(service.reconcilePending(tx.getId(), now, GIVE_UP)).isEqualTo(ReconcileOutcome.EXPIRED);
        verify(paypalClient, never()).capture(any());
    }

    // Review #251 m1: order đã COMPLETED (capture xong nhưng DB chưa ghi) -> đọc kết quả có sẵn, không capture lại
    @Test
    void paypal_alreadyCompleted_usesExistingCaptureWithoutCapturingAgain() {
        tx.setProvider(PaymentProvider.PAYPAL);
        tx.setGatewayTxnRef("ORDER1");
        tx.setAmountUsdCents(390L);
        when(paypalClient.isConfigured()).thenReturn(true);
        when(paypalClient.getOrder("ORDER1")).thenReturn(new PaypalClient.OrderSnapshot("COMPLETED",
                new PaypalClient.Capture(true, "COMPLETED", "CAP1", "USD", 390L)));

        assertThat(service.reconcilePending(tx.getId(), now, GIVE_UP)).isEqualTo(ReconcileOutcome.ACTIVATED);
        assertThat(tx.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(tx.getVnpTransactionNo()).isEqualTo("CAP1");
        verify(paypalClient, never()).capture(any());
    }

    // Review #251 M2: người mua đồng ý đơn 1 rồi trả đơn 2 -> job không được capture đơn 1 (thu tiền lần hai)
    @Test
    void paypal_recheckOfSupersededApprovedOrder_neverCaptures() {
        tx.setProvider(PaymentProvider.PAYPAL);
        tx.setGatewayTxnRef("ORDER1");
        tx.setAmountUsdCents(390L);
        tx.setStatus(PaymentStatus.EXPIRED);
        tx.setVnpResponseCode("SUPERSEDED");
        when(paypalClient.isConfigured()).thenReturn(true);
        when(paypalClient.getOrder("ORDER1")).thenReturn(new PaypalClient.OrderSnapshot("APPROVED", null));

        assertThat(service.reconcilePending(tx.getId(), now, GIVE_UP)).isEqualTo(ReconcileOutcome.SKIPPED);
        assertThat(tx.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        verify(paypalClient, never()).capture(any());
    }

    // Đơn bị thay thế nhưng thật ra đã capture xong bên PayPal (lần quay về bị lỡ): đọc kết quả có sẵn và kích hoạt
    @Test
    void paypal_recheckOfSupersededCompletedOrder_activatesWithoutCapturing() {
        tx.setProvider(PaymentProvider.PAYPAL);
        tx.setGatewayTxnRef("ORDER1");
        tx.setAmountUsdCents(390L);
        tx.setStatus(PaymentStatus.EXPIRED);
        tx.setVnpResponseCode("SUPERSEDED");
        when(paypalClient.isConfigured()).thenReturn(true);
        when(paypalClient.getOrder("ORDER1")).thenReturn(new PaypalClient.OrderSnapshot("COMPLETED",
                new PaypalClient.Capture(true, "COMPLETED", "CAP1", "USD", 390L)));

        assertThat(service.reconcilePending(tx.getId(), now, GIVE_UP)).isEqualTo(ReconcileOutcome.ACTIVATED);
        assertThat(tx.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(paypalClient, never()).capture(any());
    }

    @Test
    void paypal_lateReturnForExpiredOrder_stillCaptures() {
        tx.setProvider(PaymentProvider.PAYPAL);
        tx.setGatewayTxnRef("ORDER1");
        tx.setAmountUsdCents(390L);
        tx.setStatus(PaymentStatus.EXPIRED);
        when(transactionRepository.findByGatewayTxnRefForUpdate("ORDER1")).thenReturn(Optional.of(tx));
        when(paypalClient.capture("ORDER1")).thenReturn(new PaypalClient.Capture(true, "COMPLETED", "CAP1", "USD", 390L));

        String url = service.handlePaypalReturn("ORDER1");

        assertThat(url).contains("status=success");
        assertThat(tx.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    void checkout_expiresOlderPendingOrdersOfSameUser() {
        PaymentTransaction older = PaymentTransaction.builder().id(UUID.randomUUID()).user(owner).plan(plan)
                .amountVnd(99_000L).status(PaymentStatus.PENDING).provider(PaymentProvider.VNPAY).build();
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(planRepository.findByCode("VIP_MONTH")).thenReturn(Optional.of(plan));
        when(transactionRepository.findByUserIdAndStatus(owner.getId(), PaymentStatus.PENDING)).thenReturn(List.of(older));
        when(transactionRepository.save(any(PaymentTransaction.class))).thenAnswer(inv -> {
            PaymentTransaction saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });

        service.checkout(owner.getId(), new CheckoutRequest("VIP_MONTH", null), "127.0.0.1");

        assertThat(older.getStatus()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(older.getVnpResponseCode()).isEqualTo("SUPERSEDED");
        verify(transactionRepository).flush();
    }

    @Test
    @SuppressWarnings("unchecked")
    void vnpay_querydrRequest_isSignedInVnpayFieldOrder() {
        org.mockito.ArgumentCaptor<Map<String, String>> body = org.mockito.ArgumentCaptor.forClass(Map.class);
        doReturn(queryResponse("91", "", 0L)).when(vnpayClient).postQuery(body.capture());

        service.reconcilePending(tx.getId(), now, GIVE_UP);

        Map<String, String> b = body.getValue();
        assertThat(b.get("vnp_Command")).isEqualTo("querydr");
        assertThat(b.get("vnp_TxnRef")).isEqualTo(VnpayClient.toTxnRef(tx.getId()));
        assertThat(b.get("vnp_TransactionDate"))
                .isEqualTo(VnpayClient.toVnTime(tx.getCreatedAt()).format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        String data = String.join("|", b.get("vnp_RequestId"), b.get("vnp_Version"), b.get("vnp_Command"),
                b.get("vnp_TmnCode"), b.get("vnp_TxnRef"), b.get("vnp_TransactionDate"), b.get("vnp_CreateDate"),
                b.get("vnp_IpAddr"), b.get("vnp_OrderInfo"));
        assertThat(b.get("vnp_SecureHash")).isEqualTo(VnpayClient.hmacSha512(SECRET, data));
    }

    @Test
    void apiUrl_followsSandboxOrProductionPayUrl() {
        assertThat(VnpayClient.resolveApiUrl("", VnpayProperties.SANDBOX_PAY_URL)).isEqualTo(VnpayClient.SANDBOX_API_URL);
        assertThat(VnpayClient.resolveApiUrl(null, "https://pay.vnpay.vn/vpcpay.html")).isEqualTo(VnpayClient.PRODUCTION_API_URL);
        assertThat(VnpayClient.resolveApiUrl(" https://x/api ", VnpayProperties.SANDBOX_PAY_URL)).isEqualTo("https://x/api");
    }

    @Test
    void scheduler_reconcilesEachStaleOrderAndKeepsGoingOnError() {
        PaymentService paymentService = mock(PaymentService.class);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(transactionRepository.findIdsByStatusAndCreatedAtBefore(eq(PaymentStatus.PENDING),
                eq(now.minusMinutes(30)), any())).thenReturn(List.of(a, b));
        when(paymentService.reconcilePending(a, now, GIVE_UP)).thenThrow(new IllegalStateException("db"));
        when(paymentService.reconcilePending(b, now, GIVE_UP)).thenReturn(ReconcileOutcome.EXPIRED);
        // Đơn bị thay thế chưa trả trong 24h gần nhất cũng được hỏi lại (review #251 M1, m5)
        UUID superseded = UUID.randomUUID();
        when(transactionRepository.findUnpaidIdsByStatusAndReasonSince(eq(PaymentStatus.EXPIRED), eq("SUPERSEDED"),
                eq(now.minus(GIVE_UP)), any())).thenReturn(List.of(superseded));
        when(paymentService.reconcilePending(superseded, now, GIVE_UP)).thenReturn(ReconcileOutcome.ACTIVATED);

        PendingPaymentReconciler job = new PendingPaymentReconciler(transactionRepository, paymentService,
                Duration.ofMinutes(30), GIVE_UP, 50);
        Map<ReconcileOutcome, Integer> counts = job.reconcile(now);

        assertThat(counts).containsEntry(ReconcileOutcome.STILL_PENDING, 1).containsEntry(ReconcileOutcome.EXPIRED, 1)
                .containsEntry(ReconcileOutcome.ACTIVATED, 1);
    }
}
