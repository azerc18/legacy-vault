package com.ltld.app.legacyvault.service.paymentservice;

import com.ltld.app.legacyvault.dto.paymentdto.CheckoutRequest;
import com.ltld.app.legacyvault.dto.paymentdto.CheckoutResponse;
import com.ltld.app.legacyvault.entity.PaymentTransaction;
import com.ltld.app.legacyvault.entity.SubscriptionPlan;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServicePaypalTest {

    private static final String ORDER = "ORDER1";

    @Mock private UserRepository userRepository;
    @Mock private SubscriptionPlanRepository planRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private PaymentTransactionRepository transactionRepository;
    @Mock private EntitlementServiceImpl entitlementService;
    @Mock private VnpayClient vnpayClient;
    @Mock private PaypalClient paypalClient;
    @Mock private AuditLogService auditLogService;
    @Mock private CouponService couponService;

    private PaymentServiceImpl service;
    private User owner;
    private SubscriptionPlan plan;
    private PaymentTransaction tx;

    @BeforeEach
    void setUp() {
        service = new PaymentServiceImpl(userRepository, planRepository, subscriptionRepository, transactionRepository,
                entitlementService, vnpayClient, mock(VnpayProperties.class), paypalClient, auditLogService, couponService);
        owner = User.builder().id(UUID.randomUUID()).email("owner@example.com").build();
        plan = SubscriptionPlan.builder().code("VIP_MONTH").priceVnd(99_000L).durationDays(30).build();
        tx = PaymentTransaction.builder()
                .id(UUID.randomUUID()).user(owner).plan(plan).amountVnd(99_000L)
                .status(PaymentStatus.PENDING).provider(PaymentProvider.PAYPAL)
                .gatewayTxnRef(ORDER).amountUsdCents(390L)
                .build();
        when(transactionRepository.findByGatewayTxnRefForUpdate(ORDER)).thenReturn(Optional.of(tx));
    }

    private static PaypalClient.Capture completed(String currency, long cents) {
        return new PaypalClient.Capture(true, "COMPLETED", "CAP1", currency, cents);
    }

    @Test
    void checkout_paypal_createsOrderAndStoresRefAndUsd() {
        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(planRepository.findByCode("VIP_MONTH")).thenReturn(Optional.of(plan));
        when(transactionRepository.save(any())).thenAnswer(inv -> {
            PaymentTransaction saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });
        when(paypalClient.toUsdCents(99_000L)).thenReturn(390L);
        when(paypalClient.createOrder(any(), org.mockito.ArgumentMatchers.eq(390L), any()))
                .thenReturn(new PaypalClient.Order(ORDER, "https://paypal/approve"));

        CheckoutResponse response = service.checkout(owner.getId(),
                CheckoutRequest.builder().planCode("VIP_MONTH").provider("paypal").build(), "127.0.0.1");

        assertThat(response.getPaymentUrl()).isEqualTo("https://paypal/approve");
        org.mockito.ArgumentCaptor<PaymentTransaction> saved = org.mockito.ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(transactionRepository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        PaymentTransaction last = saved.getValue();
        assertThat(last.getProvider()).isEqualTo(PaymentProvider.PAYPAL);
        assertThat(last.getGatewayTxnRef()).isEqualTo(ORDER);
        assertThat(last.getAmountUsdCents()).isEqualTo(390L);
        verify(vnpayClient, never()).ensureConfigured();
    }

    @Test
    void checkout_unknownProvider_badRequest() {
        CheckoutRequest request = CheckoutRequest.builder().planCode("VIP_MONTH").provider("momo").build();

        assertThatThrownBy(() -> service.checkout(owner.getId(), request, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void checkout_paypalNotConfigured_propagatesServiceUnavailable() {
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "not configured"))
                .when(paypalClient).ensureConfigured();
        CheckoutRequest request = CheckoutRequest.builder().planCode("VIP_MONTH").provider("PAYPAL").build();

        assertThatThrownBy(() -> service.checkout(owner.getId(), request, "127.0.0.1"))
                .isInstanceOf(BusinessException.class);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void return_completedCapture_activatesPlanAndStoresCaptureId() {
        when(paypalClient.capture(ORDER)).thenReturn(completed("USD", 390L));
        when(subscriptionRepository.findFirstByUserIdAndStatusOrderByExpiresAtDesc(owner.getId(), SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.empty());

        String url = service.handlePaypalReturn(ORDER);

        assertThat(url).contains("status=success");
        assertThat(tx.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(tx.getPaidAt()).isNotNull();
        assertThat(tx.getVnpTransactionNo()).isEqualTo("CAP1");
        verify(subscriptionRepository).save(any());
        verify(auditLogService).log(org.mockito.ArgumentMatchers.eq(AuditAction.PAYMENT_SUCCEEDED), any(), any(), any(), any(), any(), any());
    }

    @Test
    void return_alreadySuccessful_doesNotCaptureOrExtendAgain() {
        tx.setStatus(PaymentStatus.SUCCESS);

        String url = service.handlePaypalReturn(ORDER);

        assertThat(url).contains("status=success");
        verify(paypalClient, never()).capture(any());
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void return_declined_marksFailedWithoutActivation() {
        when(paypalClient.capture(ORDER)).thenReturn(new PaypalClient.Capture(false, "HTTP_422", null, null, -1L));

        String url = service.handlePaypalReturn(ORDER);

        assertThat(url).contains("status=failed");
        assertThat(tx.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void return_amountMismatch_marksFailedWithoutActivation() {
        when(paypalClient.capture(ORDER)).thenReturn(completed("USD", 1L));

        String url = service.handlePaypalReturn(ORDER);

        assertThat(url).contains("status=failed").contains("PAYMENT_AMOUNT_MISMATCH");
        assertThat(tx.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void return_wrongCurrency_marksFailed() {
        when(paypalClient.capture(ORDER)).thenReturn(completed("EUR", 390L));

        assertThat(service.handlePaypalReturn(ORDER)).contains("PAYMENT_AMOUNT_MISMATCH");
        assertThat(tx.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void return_gatewayError_staysPendingSoItCanBeRetried() {
        when(paypalClient.capture(ORDER)).thenThrow(new IllegalStateException("timeout"));

        String url = service.handlePaypalReturn(ORDER);

        assertThat(url).contains("status=failed").contains("SERVICE_UNAVAILABLE");
        assertThat(tx.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void return_unknownOrMissingOrder_failsWithoutCapture() {
        when(transactionRepository.findByGatewayTxnRefForUpdate("nope")).thenReturn(Optional.empty());

        assertThat(service.handlePaypalReturn("nope")).contains("status=failed");
        assertThat(service.handlePaypalReturn(null)).contains("status=failed");
        assertThat(service.handlePaypalReturn(" ")).contains("status=failed");
        verify(paypalClient, never()).capture(any());
    }

    @Test
    void return_vnpayTransactionWithSameRef_isIgnored() {
        tx.setProvider(PaymentProvider.VNPAY);

        assertThat(service.handlePaypalReturn(ORDER)).contains("status=failed");
        verify(paypalClient, never()).capture(any());
    }

    @Test
    void cancel_pending_marksFailed() {
        assertThat(service.handlePaypalCancel(ORDER)).contains("status=failed");
        assertThat(tx.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(paypalClient, never()).capture(any());
    }

    @Test
    void cancel_alreadySuccessful_isNotDowngraded() {
        tx.setStatus(PaymentStatus.SUCCESS);

        service.handlePaypalCancel(ORDER);

        assertThat(tx.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    void availableProviders_listsOnlyConfiguredGateways() {
        when(vnpayClient.isConfigured()).thenReturn(true);
        when(paypalClient.isConfigured()).thenReturn(false);
        assertThat(service.getAvailableProviders()).isEqualTo(List.of("VNPAY"));

        when(paypalClient.isConfigured()).thenReturn(true);
        assertThat(service.getAvailableProviders()).isEqualTo(List.of("VNPAY", "PAYPAL"));
    }
}
