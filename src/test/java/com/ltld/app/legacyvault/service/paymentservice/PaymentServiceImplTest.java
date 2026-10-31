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
import com.ltld.app.legacyvault.exception.PaymentException;
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

    private VnpayClient vnpayClient;
    private PaymentServiceImpl paymentService;

    private User owner;
    private SubscriptionPlan vipMonthPlan;

    @BeforeEach
    void setUp() {
        vnpayClient = new VnpayClient(
                "DEMO_TMN",
                "SECRET_KEY_FOR_TESTING_1234567890",
                "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
                "http://localhost:8084/api/payments/vnpay-return"
        );
        paymentService = new PaymentServiceImpl(
                userRepository,
                subscriptionPlanRepository,
                subscriptionRepository,
                paymentTransactionRepository,
                entitlementService,
                vnpayClient,
                auditLogService
        );

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

    // Case 1: chữ ký sai -> bị từ chối (PAYMENT_SIGNATURE_INVALID)
    @Test
    void handleVnpayIpn_invalidSignature_throwsPaymentSignatureInvalid() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", UUID.randomUUID().toString());
        params.put("vnp_Amount", "9900000");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_SecureHash", "deadbeef_invalid_hash");

        PaymentException ex = assertThrows(
                PaymentException.class,
                () -> paymentService.handleVnpayIpn(params)
        );
        assertEquals(ErrorCode.PAYMENT_SIGNATURE_INVALID, ex.getErrorCode());
        verify(paymentTransactionRepository, never()).save(any());
        verify(subscriptionRepository, never()).save(any());
    }

    // Case 2: IPN gọi trùng -> idempotent (RspCode=02, chỉ kích hoạt 1 lần)
    @Test
    void handleVnpayIpn_duplicateCall_isIdempotentAndActivatesOnlyOnce() {
        UUID txId = UUID.randomUUID();
        PaymentTransaction tx = PaymentTransaction.builder()
                .id(txId)
                .user(owner)
                .plan(vipMonthPlan)
                .amountVnd(99_000L)
                .status(PaymentStatus.PENDING)
                .build();

        when(paymentTransactionRepository.findByIdForUpdate(txId)).thenReturn(Optional.of(tx));
        when(subscriptionRepository.findFirstByUserIdAndStatusOrderByExpiresAtDesc(owner.getId(), SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.empty());

        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", txId.toString());
        params.put("vnp_Amount", "9900000");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionNo", "14567890");
        params.put("vnp_BankCode", "NCB");
        params.put("vnp_SecureHash", vnpayClient.signParams(params));

        Map<String, String> firstResp = paymentService.handleVnpayIpn(params);
        assertEquals("00", firstResp.get("RspCode"));
        assertEquals(PaymentStatus.SUCCESS, tx.getStatus());

        // Lần gọi thứ 2 với cùng params -> trả về RspCode=02, không tạo subscription mới
        Map<String, String> secondResp = paymentService.handleVnpayIpn(params);
        assertEquals("02", secondResp.get("RspCode"));
        verify(subscriptionRepository, times(1)).save(any(Subscription.class));
    }

    // Case 3: số tiền lệch -> FAILED + PAYMENT_AMOUNT_MISMATCH
    @Test
    void handleVnpayIpn_amountMismatch_marksFailedAndThrowsPaymentAmountMismatch() {
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

        PaymentException ex = assertThrows(
                PaymentException.class,
                () -> paymentService.handleVnpayIpn(params)
        );
        assertEquals(ErrorCode.PAYMENT_AMOUNT_MISMATCH, ex.getErrorCode());
        assertEquals(PaymentStatus.FAILED, tx.getStatus());

        ArgumentCaptor<PaymentTransaction> captor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(paymentTransactionRepository).save(captor.capture());
        assertEquals(PaymentStatus.FAILED, captor.getValue().getStatus());
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
                new CheckoutRequest("VIP_MONTH"),
                "127.0.0.1"
        );

        assertNotNull(response.getTransactionId());
        assertEquals("VIP_MONTH", response.getPlanCode());
        assertEquals(99_000L, response.getAmountVnd());
        assertTrue(response.getPaymentUrl().contains("vnp_SecureHash="));
    }

    @Test
    void checkout_whenVnpayNotConfigured_throwsServiceUnavailable() {
        VnpayClient unconfiguredVnpay = new VnpayClient("", "", "https://sandbox.vnpayment.vn", "");
        PaymentServiceImpl unconfiguredPaymentService = new PaymentServiceImpl(
                userRepository,
                subscriptionPlanRepository,
                subscriptionRepository,
                paymentTransactionRepository,
                entitlementService,
                unconfiguredVnpay,
                auditLogService
        );

        CheckoutRequest request = CheckoutRequest.builder().planCode("VIP_MONTH").build();
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> unconfiguredPaymentService.checkout(owner.getId(), request, "127.0.0.1")
        );
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, ex.getErrorCode());
    }
}
