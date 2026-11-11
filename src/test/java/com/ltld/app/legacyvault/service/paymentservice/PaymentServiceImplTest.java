package com.ltld.app.legacyvault.service.paymentservice;

import com.ltld.app.legacyvault.dto.paymentdto.CheckoutRequest;
import com.ltld.app.legacyvault.dto.paymentdto.CheckoutResponse;
import com.ltld.app.legacyvault.entity.PaymentTransaction;
import com.ltld.app.legacyvault.entity.Subscription;
import com.ltld.app.legacyvault.entity.SubscriptionPlan;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.PaymentStatus;
import com.ltld.app.legacyvault.enums.SubscriptionStatus;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.repository.PaymentTransactionRepository;
import com.ltld.app.legacyvault.repository.SubscriptionPlanRepository;
import com.ltld.app.legacyvault.repository.SubscriptionRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private SubscriptionPlanRepository subscriptionPlanRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private PaymentTransactionRepository paymentTransactionRepository;
    @Mock private EntitlementServiceImpl entitlementService;
    @Mock private AuditLogService auditLogService;
    @Mock private PaypalClient paypalClient;
    @Mock private CouponService couponService;

    private VnpayClient vnpayClient;
    private PaymentServiceImpl paymentService;

    private static VnpayProperties vnpayProperties(boolean activateOnReturn) {
        return new VnpayProperties(
                "DEMO_TMN",
                "SECRET_KEY_FOR_TESTING_1234567890",
                "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
                "http://localhost:8084/api/payments/vnpay-return",
                activateOnReturn);
    }

    private PaymentServiceImpl newPaymentService(VnpayClient client, VnpayProperties properties) {
        return new PaymentServiceImpl(
                userRepository,
                subscriptionPlanRepository,
                subscriptionRepository,
                paymentTransactionRepository,
                entitlementService,
                client,
                properties,
                paypalClient,
                auditLogService,
                couponService
        );
    }

    private User owner;
    private SubscriptionPlan vipMonthPlan;

    @BeforeEach
    void setUp() {
        vnpayClient = new VnpayClient(vnpayProperties(false), "http://localhost:5173");
        paymentService = newPaymentService(vnpayClient, vnpayProperties(false));

        owner = User.builder()
                .id(UUID.randomUUID())
                .email("owner@example.com")
                .fullName("Owner User")
                .build();

        vipMonthPlan = SubscriptionPlan.builder()
                .id(UUID.randomUUID())
                .code("VIP_MONTH")
                .name("VIP Monthly")
                .priceVnd(99_000L)
                .durationDays(30)
                .maxVaults(5)
                .maxBeneficiaries(10)
                .storageQuotaBytes(5_368_709_120L)
                .legacyMessagesEnabled(true)
                .build();
    }

    // IPN Case 1: sai chữ ký -> trả {RspCode: "97", Message: "Invalid Checksum"}, không ném exception
    @Test
    void handleVnpayIpn_invalidSignature_returns97() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", UUID.randomUUID().toString());
        params.put("vnp_Amount", "9900000");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_SecureHash", "deadbeef_invalid_hash");

        Map<String, String> resp = paymentService.handleVnpayIpn(params);
        assertEquals("97", resp.get("RspCode"));
        assertEquals("Invalid Checksum", resp.get("Message"));
        verify(paymentTransactionRepository, never()).save(any());
        verify(subscriptionRepository, never()).save(any());
    }

    // IPN Case 2: không thấy đơn -> trả {RspCode: "01", Message: "Order not found"}
    @Test
    void handleVnpayIpn_orderNotFound_returns01() {
        UUID txId = UUID.randomUUID();
        when(paymentTransactionRepository.findByIdForUpdate(txId)).thenReturn(Optional.empty());

        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", txId.toString());
        params.put("vnp_Amount", "9900000");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_SecureHash", vnpayClient.signParams(params));

        Map<String, String> resp = paymentService.handleVnpayIpn(params);
        assertEquals("01", resp.get("RspCode"));
        assertEquals("Order not found", resp.get("Message"));
    }

    // IPN Case 3: IPN gọi trùng (status khác PENDING) -> {RspCode: "02", Message: "Order already confirmed"}
    @Test
    void handleVnpayIpn_duplicateCall_isIdempotentAndReturns02() {
        UUID txId = UUID.randomUUID();
        PaymentTransaction tx = PaymentTransaction.builder()
                .id(txId)
                .user(owner)
                .plan(vipMonthPlan)
                .amountVnd(99_000L)
                .status(PaymentStatus.SUCCESS)
                .build();

        when(paymentTransactionRepository.findByIdForUpdate(txId)).thenReturn(Optional.of(tx));

        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", txId.toString());
        params.put("vnp_Amount", "9900000");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_SecureHash", vnpayClient.signParams(params));

        Map<String, String> resp = paymentService.handleVnpayIpn(params);
        assertEquals("02", resp.get("RspCode"));
        assertEquals("Order already confirmed", resp.get("Message"));
        verify(subscriptionRepository, never()).save(any());
    }

    // IPN Case 4: số tiền lệch -> lưu FAILED + audit PAYMENT_FAILED, trả {RspCode: "04", Message: "Invalid amount"}
    @Test
    void handleVnpayIpn_amountMismatch_marksFailedAndReturns04() {
        UUID txId = UUID.randomUUID();
        PaymentTransaction tx = PaymentTransaction.builder()
                .id(txId)
                .user(owner)
                .plan(vipMonthPlan)
                .amountVnd(99_000L)
                .status(PaymentStatus.PENDING)
                .build();

        when(paymentTransactionRepository.findByIdForUpdate(txId)).thenReturn(Optional.of(tx));

        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", txId.toString());
        params.put("vnp_Amount", "5000000"); // 50,000 VND != 99,000 VND
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_SecureHash", vnpayClient.signParams(params));

        Map<String, String> resp = paymentService.handleVnpayIpn(params);
        assertEquals("04", resp.get("RspCode"));
        assertEquals("Invalid amount", resp.get("Message"));

        assertEquals(PaymentStatus.FAILED, tx.getStatus());
        verify(paymentTransactionRepository).save(tx);
        verify(subscriptionRepository, never()).save(any());
        verify(auditLogService).log(
                eq(AuditAction.PAYMENT_FAILED),
                eq(AuditResult.FAILURE),
                eq(owner.getId()),
                eq(owner.getEmail()),
                eq("PaymentTransaction"),
                eq(txId.toString()),
                any()
        );
    }

    // IPN Case 5: vnp_ResponseCode == "00" VÀ vnp_TransactionStatus == "00" -> kích hoạt gói, trả 00 "Confirm Success"
    @Test
    void handleVnpayIpn_success_activatesAndReturns00() {
        UUID txId = UUID.randomUUID();
        PaymentTransaction tx = PaymentTransaction.builder()
                .id(txId)
                .user(owner)
                .plan(vipMonthPlan)
                .amountVnd(99_000L)
                .status(PaymentStatus.PENDING)
                .build();

        when(paymentTransactionRepository.findByIdForUpdate(txId)).thenReturn(Optional.of(tx));
        when(userRepository.findByIdForUpdate(owner.getId())).thenReturn(Optional.of(owner));
        when(subscriptionRepository.findFirstByUserIdAndStatusOrderByExpiresAtDesc(owner.getId(), SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.empty());

        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", txId.toString());
        params.put("vnp_Amount", "9900000");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_TransactionNo", "14567890");
        params.put("vnp_BankCode", "NCB");
        params.put("vnp_SecureHash", vnpayClient.signParams(params));

        Map<String, String> resp = paymentService.handleVnpayIpn(params);
        assertEquals("00", resp.get("RspCode"));
        assertEquals("Confirm Success", resp.get("Message"));
        assertEquals(PaymentStatus.SUCCESS, tx.getStatus());

        verify(userRepository).findByIdForUpdate(owner.getId());
        verify(subscriptionRepository, times(1)).save(any(Subscription.class));
        verify(auditLogService).log(
                eq(AuditAction.PAYMENT_SUCCEEDED),
                eq(AuditResult.SUCCESS),
                eq(owner.getId()),
                eq(owner.getEmail()),
                eq("PaymentTransaction"),
                eq(txId.toString()),
                eq("VIP_MONTH")
        );
    }

    // Việc 6: Owner đã hủy gia hạn (đang dùng thử) rồi trả tiền gia hạn -> bỏ cờ hủy cuối kỳ và cờ dùng thử, hạn cộng nối tiếp
    @Test
    void handleVnpayIpn_renewAfterCancel_clearsCancelFlagAndExtends() {
        UUID txId = UUID.randomUUID();
        PaymentTransaction tx = PaymentTransaction.builder()
                .id(txId).user(owner).plan(vipMonthPlan).amountVnd(99_000L).status(PaymentStatus.PENDING).build();
        java.time.LocalDateTime oldExpiry = java.time.LocalDateTime.now().plusDays(5);
        Subscription running = Subscription.builder()
                .id(UUID.randomUUID()).user(owner).plan(vipMonthPlan).status(SubscriptionStatus.ACTIVE)
                .startedAt(oldExpiry.minusDays(30)).expiresAt(oldExpiry)
                .cancelAtPeriodEnd(true).cancelledAt(java.time.LocalDateTime.now().minusDays(2)).trial(true).build();

        when(paymentTransactionRepository.findByIdForUpdate(txId)).thenReturn(Optional.of(tx));
        when(userRepository.findByIdForUpdate(owner.getId())).thenReturn(Optional.of(owner));
        when(subscriptionRepository.findFirstByUserIdAndStatusOrderByExpiresAtDesc(owner.getId(), SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(running));

        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", txId.toString());
        params.put("vnp_Amount", "9900000");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_TransactionNo", "14567891");
        params.put("vnp_BankCode", "NCB");
        params.put("vnp_SecureHash", vnpayClient.signParams(params));

        assertEquals("00", paymentService.handleVnpayIpn(params).get("RspCode"));
        assertEquals(false, running.isCancelAtPeriodEnd());
        assertEquals(null, running.getCancelledAt());
        // review #256 m2: trả tiền trong lúc dùng thử thì kỳ này không còn là dùng thử
        assertEquals(false, running.isTrial());
        assertEquals(oldExpiry.plusDays(30), running.getExpiresAt());
    }

    // IPN với vnp_TxnRef dạng 32 hex (định dạng gửi VNPay từ bản sửa "Invalid data format") vẫn tìm được đơn và kích hoạt gói
    @Test
    void handleVnpayIpn_hexTxnRef_activatesAndReturns00() {
        UUID txId = UUID.randomUUID();
        PaymentTransaction tx = PaymentTransaction.builder()
                .id(txId)
                .user(owner)
                .plan(vipMonthPlan)
                .amountVnd(99_000L)
                .status(PaymentStatus.PENDING)
                .build();

        when(paymentTransactionRepository.findByIdForUpdate(txId)).thenReturn(Optional.of(tx));
        when(userRepository.findByIdForUpdate(owner.getId())).thenReturn(Optional.of(owner));
        when(subscriptionRepository.findFirstByUserIdAndStatusOrderByExpiresAtDesc(owner.getId(), SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.empty());

        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", txId.toString().replace("-", ""));
        params.put("vnp_Amount", "9900000");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_SecureHash", vnpayClient.signParams(params));

        Map<String, String> resp = paymentService.handleVnpayIpn(params);
        assertEquals("00", resp.get("RspCode"));
        assertEquals(PaymentStatus.SUCCESS, tx.getStatus());
        verify(subscriptionRepository, times(1)).save(any(Subscription.class));
    }

    // IPN Case 6: Cổng thanh toán báo lỗi (vnp_ResponseCode != "00" hoặc vnp_TransactionStatus != "00") -> lưu FAILED, trả 00
    @Test
    void handleVnpayIpn_gatewayFailed_marksFailedAndReturns00() {
        UUID txId = UUID.randomUUID();
        PaymentTransaction tx = PaymentTransaction.builder()
                .id(txId)
                .user(owner)
                .plan(vipMonthPlan)
                .amountVnd(99_000L)
                .status(PaymentStatus.PENDING)
                .build();

        when(paymentTransactionRepository.findByIdForUpdate(txId)).thenReturn(Optional.of(tx));

        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", txId.toString());
        params.put("vnp_Amount", "9900000");
        params.put("vnp_ResponseCode", "24"); // Khách hàng hủy giao dịch
        params.put("vnp_TransactionStatus", "02");
        params.put("vnp_SecureHash", vnpayClient.signParams(params));

        Map<String, String> resp = paymentService.handleVnpayIpn(params);
        assertEquals("00", resp.get("RspCode"));
        assertEquals("Confirm Success", resp.get("Message"));
        assertEquals(PaymentStatus.FAILED, tx.getStatus());

        verify(subscriptionRepository, never()).save(any());
        verify(auditLogService).log(
                eq(AuditAction.PAYMENT_FAILED),
                eq(AuditResult.FAILURE),
                eq(owner.getId()),
                eq(owner.getEmail()),
                eq("PaymentTransaction"),
                eq(txId.toString()),
                any()
        );
    }

    // Return URL Case 1: sai chữ ký -> redirect failed kèm &errorCode=PAYMENT_SIGNATURE_INVALID, KHÔNG ném exception
    @Test
    void handleVnpayReturn_invalidSignature_redirectsWithErrorCode() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", UUID.randomUUID().toString());
        params.put("vnp_SecureHash", "invalid_signature");

        String redirectUrl = paymentService.handleVnpayReturn(params);
        assertTrue(redirectUrl.contains("/payment/result?status=failed"));
        assertTrue(redirectUrl.contains("errorCode=PAYMENT_SIGNATURE_INVALID"));
        verify(subscriptionRepository, never()).save(any());
    }

    // Return URL Case 2: chữ ký đúng, mặc định activate-on-return = false -> redirect success, KHÔNG kích hoạt gói
    @Test
    void handleVnpayReturn_validSignature_default_doesNotActivate() {
        UUID txId = UUID.randomUUID();
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", txId.toString());
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_SecureHash", vnpayClient.signParams(params));

        String redirectUrl = paymentService.handleVnpayReturn(params);
        assertTrue(redirectUrl.contains("/payment/result?status=success"));
        assertTrue(redirectUrl.contains("txnRef=" + txId));
        // Mặc định false -> không kích hoạt
        verify(subscriptionRepository, never()).save(any());
    }

    // Return URL Case 3: activate-on-return = true (dev local) -> kích hoạt gói
    @Test
    void handleVnpayReturn_validSignature_activateOnReturn_activates() {
        paymentService = newPaymentService(vnpayClient, vnpayProperties(true));

        UUID txId = UUID.randomUUID();
        PaymentTransaction tx = PaymentTransaction.builder()
                .id(txId)
                .user(owner)
                .plan(vipMonthPlan)
                .amountVnd(99_000L)
                .status(PaymentStatus.PENDING)
                .build();

        when(paymentTransactionRepository.findByIdForUpdate(txId)).thenReturn(Optional.of(tx));
        when(userRepository.findByIdForUpdate(owner.getId())).thenReturn(Optional.of(owner));
        when(subscriptionRepository.findFirstByUserIdAndStatusOrderByExpiresAtDesc(owner.getId(), SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.empty());

        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", txId.toString());
        params.put("vnp_Amount", "9900000");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_SecureHash", vnpayClient.signParams(params));

        String redirectUrl = paymentService.handleVnpayReturn(params);
        assertTrue(redirectUrl.contains("/payment/result?status=success"));
        assertTrue(redirectUrl.contains("txnRef=" + txId));

        verify(subscriptionRepository, times(1)).save(any(Subscription.class));
    }

    @Test
    void checkout_createsPendingTransactionAndReturnsSignedUrl() {
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(subscriptionPlanRepository.findByCode("VIP_MONTH")).thenReturn(Optional.of(vipMonthPlan));
        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenAnswer(inv -> {
            PaymentTransaction saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        CheckoutResponse response = paymentService.checkout(
                owner.getId(),
                new CheckoutRequest("VIP_MONTH", null),
                "127.0.0.1"
        );

        assertNotNull(response.getTransactionId());
        assertEquals("VIP_MONTH", response.getPlanCode());
        assertEquals(99_000L, response.getAmountVnd());
        assertTrue(response.getPaymentUrl().contains("vnp_SecureHash="));
    }

    @Test
    void checkout_whenVnpayNotConfigured_throwsServiceUnavailable() {
        VnpayProperties empty = new VnpayProperties("", "", null, null, false);
        PaymentServiceImpl unconfiguredPaymentService = newPaymentService(new VnpayClient(empty), empty);

        CheckoutRequest request = CheckoutRequest.builder().planCode("VIP_MONTH").build();
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> unconfiguredPaymentService.checkout(owner.getId(), request, "127.0.0.1")
        );
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, ex.getErrorCode());
    }

    // Việc 4: checkout có mã -> giao dịch và URL VNPay dùng giá sau giảm, nhớ mã và số tiền giảm
    @Test
    void checkout_withCoupon_chargesDiscountedAmount() {
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(subscriptionPlanRepository.findByCode("VIP_MONTH")).thenReturn(Optional.of(vipMonthPlan));
        when(couponService.quote(owner.getId(), "tet2027", vipMonthPlan))
                .thenReturn(new com.ltld.app.legacyvault.dto.coupondto.CouponQuote("VIP_MONTH", 99_000L, 20_000L, 79_000L));
        org.mockito.ArgumentCaptor<PaymentTransaction> saved = org.mockito.ArgumentCaptor.forClass(PaymentTransaction.class);
        when(paymentTransactionRepository.save(saved.capture())).thenAnswer(inv -> {
            PaymentTransaction tx = inv.getArgument(0);
            tx.setId(UUID.randomUUID());
            return tx;
        });

        CheckoutResponse response = paymentService.checkout(owner.getId(),
                new CheckoutRequest("VIP_MONTH", null, "tet2027"), "127.0.0.1");

        assertEquals(79_000L, response.getAmountVnd());
        assertEquals(20_000L, response.getDiscountVnd());
        assertTrue(response.getPaymentUrl().contains("vnp_Amount=7900000"));
        PaymentTransaction tx = saved.getValue();
        assertEquals(79_000L, tx.getAmountVnd());
        assertEquals("TET2027", tx.getCouponCode());
        assertEquals(20_000L, tx.getDiscountVnd());
    }

    // Việc 4: mã sai thì không tạo đơn và không đóng đơn đang chờ
    @Test
    void checkout_withInvalidCoupon_createsNothing() {
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(subscriptionPlanRepository.findByCode("VIP_MONTH")).thenReturn(Optional.of(vipMonthPlan));
        when(couponService.quote(any(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.COUPON_INVALID, "bad"));

        BusinessException ex = assertThrows(BusinessException.class, () -> paymentService.checkout(owner.getId(),
                new CheckoutRequest("VIP_MONTH", null, "BAD"), "127.0.0.1"));
        assertEquals(ErrorCode.COUPON_INVALID, ex.getErrorCode());
        verify(paymentTransactionRepository, never()).save(any());
        verify(paymentTransactionRepository, never()).findByUserIdAndStatus(any(), any());
    }

    // Việc 4: lượt dùng mã chỉ tăng khi giao dịch thành công
    @Test
    void ipnSuccess_withCoupon_redeemsCode() {
        UUID txId = UUID.randomUUID();
        PaymentTransaction tx = PaymentTransaction.builder().id(txId).user(owner).plan(vipMonthPlan)
                .amountVnd(79_000L).couponCode("TET2027").discountVnd(20_000L).status(PaymentStatus.PENDING).build();
        when(paymentTransactionRepository.findByIdForUpdate(txId)).thenReturn(Optional.of(tx));
        when(userRepository.findByIdForUpdate(owner.getId())).thenReturn(Optional.of(owner));
        when(subscriptionRepository.findFirstByUserIdAndStatusOrderByExpiresAtDesc(owner.getId(), SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.empty());
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", txId.toString());
        params.put("vnp_Amount", "7900000");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_SecureHash", vnpayClient.signParams(params));

        assertEquals("00", paymentService.handleVnpayIpn(params).get("RspCode"));
        verify(couponService).redeem(tx);
    }
}
