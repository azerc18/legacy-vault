package com.ltld.app.legacyvault.service.adminoverviewservice;

import com.ltld.app.legacyvault.dto.adminoverviewdto.GrantVipRequest;
import com.ltld.app.legacyvault.dto.adminoverviewdto.RefundPaymentRequest;
import com.ltld.app.legacyvault.dto.adminoverviewdto.VipGrantResponse;
import com.ltld.app.legacyvault.entity.PaymentTransaction;
import com.ltld.app.legacyvault.entity.Role;
import com.ltld.app.legacyvault.entity.Subscription;
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
import com.ltld.app.legacyvault.service.notificationservice.InAppNotifier;
import com.ltld.app.legacyvault.service.paymentservice.PaypalClient;
import com.ltld.app.legacyvault.service.paymentservice.VnpayClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Việc 3: admin tặng VIP và hoàn tiền. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminBillingServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private SubscriptionPlanRepository planRepository;
    @Mock private PaymentTransactionRepository txRepository;
    @Mock private VnpayClient vnpayClient;
    @Mock private PaypalClient paypalClient;
    @Mock private AuditLogService auditLogService;
    @Mock private InAppNotifier inAppNotifier;
    @Mock private AdminPaymentService adminPaymentService;

    private AdminBillingService service;
    private final UUID adminId = UUID.randomUUID();
    private User owner;
    private SubscriptionPlan vipMonth;
    private PaymentTransaction tx;

    @BeforeEach
    void setUp() {
        service = new AdminBillingService(userRepository, subscriptionRepository, planRepository, txRepository,
                vnpayClient, paypalClient, auditLogService, inAppNotifier, adminPaymentService);
        owner = User.builder().id(UUID.randomUUID()).email("owner@example.com")
                .roles(Set.of(Role.builder().code("OWNER").build())).build();
        vipMonth = SubscriptionPlan.builder().code("VIP_MONTH").name("VIP Monthly").priceVnd(99_000).durationDays(30).build();
        tx = PaymentTransaction.builder().id(UUID.randomUUID()).user(owner).plan(vipMonth).amountVnd(99_000)
                .status(PaymentStatus.SUCCESS).provider(PaymentProvider.VNPAY).vnpTransactionNo("14000001")
                .createdAt(LocalDateTime.now().minusDays(2)).paidAt(LocalDateTime.now().minusDays(2)).build();
        when(userRepository.findByIdForUpdate(owner.getId())).thenReturn(Optional.of(owner));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(User.builder().id(adminId).email("admin@lv.local").build()));
        when(planRepository.findByCode("VIP_MONTH")).thenReturn(Optional.of(vipMonth));
        when(txRepository.findByIdForUpdate(tx.getId())).thenReturn(Optional.of(tx));
    }

    private Subscription activeSub(LocalDateTime expiresAt) {
        Subscription sub = Subscription.builder().user(owner).plan(vipMonth).status(SubscriptionStatus.ACTIVE)
                .startedAt(LocalDateTime.now().minusDays(10)).expiresAt(expiresAt).reminderSent(true).build();
        when(subscriptionRepository.findFirstByUserIdAndStatusOrderByExpiresAtDesc(owner.getId(), SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(sub));
        return sub;
    }

    @Test
    void grant_freeOwner_createsVipFromNow() {
        VipGrantResponse r = service.grantVip(adminId, owner.getId(), new GrantVipRequest(14, "Bù lỗi hệ thống"));

        ArgumentCaptor<Subscription> saved = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(saved.capture());
        assertThat(saved.getValue().getPlan().getCode()).isEqualTo("VIP_MONTH");
        assertThat(saved.getValue().getExpiresAt()).isCloseTo(LocalDateTime.now().plusDays(14), within(5, ChronoUnit.SECONDS));
        assertThat(r.expiresAt()).isEqualTo(saved.getValue().getExpiresAt());
        verify(auditLogService).log(eq(AuditAction.ADMIN_VIP_GRANTED), any(), eq(adminId), any(), any(), any(), any());
        verify(inAppNotifier).toUser(eq(owner.getId()), any());
    }

    @Test
    void grant_activeVip_addsToCurrentExpiry() {
        LocalDateTime expiry = LocalDateTime.now().plusDays(20);
        Subscription sub = activeSub(expiry);

        service.grantVip(adminId, owner.getId(), new GrantVipRequest(10, "Quà"));

        assertThat(sub.getExpiresAt()).isEqualTo(expiry.plusDays(10));
        assertThat(sub.isReminderSent()).isFalse();
    }

    @Test
    void grant_nonOwner_isRejected() {
        owner.setRoles(Set.of(Role.builder().code("LEGAL_VERIFIER").build()));

        assertThatThrownBy(() -> service.grantVip(adminId, owner.getId(), new GrantVipRequest(5, "x")))
                .isInstanceOf(BusinessException.class);
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void refund_vnpay_callsGatewayMarksRefundedAndRemovesDays() {
        LocalDateTime expiry = LocalDateTime.now().plusDays(50);
        Subscription sub = activeSub(expiry);
        when(vnpayClient.refund(eq(tx.getId()), eq(99_000L), eq("14000001"), any(), eq("admin@lv.local")))
                .thenReturn(new VnpayClient.RefundResult(true, "00", "OK", "14000099"));

        service.refund(adminId, tx.getId(), new RefundPaymentRequest("Khách yêu cầu", null));

        assertThat(tx.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(tx.getRefundRef()).isEqualTo("14000099");
        assertThat(tx.getRefundReason()).isEqualTo("Khách yêu cầu");
        // Trả 2 ngày trước, gói 30 ngày: còn 28 ngày chưa dùng -> trừ 28 ngày (review #254 m2)
        assertThat(sub.getExpiresAt()).isCloseTo(expiry.minusDays(28), within(5, ChronoUnit.SECONDS));
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        verify(auditLogService).log(eq(AuditAction.PAYMENT_REFUNDED), any(), eq(adminId), any(), any(), any(), any());
    }

    // Review #254 m2: kỳ của giao dịch cũ đã dùng hết -> không trừ vào kỳ gia hạn mới hơn
    @Test
    void refund_fullyUsedOldPayment_keepsCurrentExpiry() {
        tx.setPaidAt(LocalDateTime.now().minusDays(40));
        LocalDateTime expiry = LocalDateTime.now().plusDays(20);
        Subscription sub = activeSub(expiry);

        service.refund(adminId, tx.getId(), new RefundPaymentRequest("Hoàn giao dịch cũ", false));

        assertThat(tx.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(sub.getExpiresAt()).isEqualTo(expiry);
        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void unusedPart_isClampedBetweenZeroAndPlanLength() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 11, 12, 0);
        tx.setPaidAt(now.minusDays(10));
        assertThat(AdminBillingService.unusedPart(tx, now)).isEqualTo(java.time.Duration.ofDays(20));
        tx.setPaidAt(now.minusDays(31));
        assertThat(AdminBillingService.unusedPart(tx, now)).isZero();
        tx.setPaidAt(now.plusDays(3));   // đồng hồ lệch: không trừ quá số ngày gói
        assertThat(AdminBillingService.unusedPart(tx, now)).isEqualTo(java.time.Duration.ofDays(30));
    }

    // Review #254 m3: thông báo chỉ gửi sau commit; n1: audit tặng VIP có email admin
    @Test
    void grant_notifiesOnlyAfterCommit_andAuditsAdminEmail() {
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            service.grantVip(adminId, owner.getId(), new GrantVipRequest(7, "Bù lỗi"));
            verify(inAppNotifier, never()).toUser(any(), any());

            org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
                    .forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);
            verify(inAppNotifier).toUser(eq(owner.getId()), any());
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
        verify(auditLogService).log(eq(AuditAction.ADMIN_VIP_GRANTED), any(), eq(adminId), eq("admin@lv.local"),
                any(), any(), any());
    }

    @Test
    void refund_lastPaidPeriod_expiresSubscriptionNow() {
        Subscription sub = activeSub(LocalDateTime.now().plusDays(28));
        when(vnpayClient.refund(any(), anyLong(), any(), any(), any()))
                .thenReturn(new VnpayClient.RefundResult(true, "00", "OK", "1"));

        service.refund(adminId, tx.getId(), new RefundPaymentRequest("Hoàn", true));

        assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
    }

    @Test
    void refund_gatewayRefuses_changesNothing() {
        activeSub(LocalDateTime.now().plusDays(28));
        when(vnpayClient.refund(any(), anyLong(), any(), any(), any()))
                .thenReturn(new VnpayClient.RefundResult(false, "95", "Giao dịch không thành công", null));

        assertThatThrownBy(() -> service.refund(adminId, tx.getId(), new RefundPaymentRequest("Hoàn", true)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.UNPROCESSABLE_ENTITY);
        assertThat(tx.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void refund_manual_doesNotCallGateway() {
        activeSub(LocalDateTime.now().plusDays(60));

        service.refund(adminId, tx.getId(), new RefundPaymentRequest("Đã chuyển khoản trả", false));

        assertThat(tx.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(tx.getRefundRef()).isNull();
        verify(vnpayClient, never()).refund(any(), anyLong(), any(), any(), any());
        verify(paypalClient, never()).refundCapture(anyString());
    }

    @Test
    void refund_paypal_refundsCapture() {
        tx.setProvider(PaymentProvider.PAYPAL);
        tx.setVnpTransactionNo("CAP1");
        activeSub(LocalDateTime.now().plusDays(60));
        when(paypalClient.refundCapture("CAP1")).thenReturn(new PaypalClient.Refund(true, "COMPLETED", "RF1"));

        service.refund(adminId, tx.getId(), new RefundPaymentRequest("Hoàn", true));

        assertThat(tx.getRefundRef()).isEqualTo("RF1");
        assertThat(tx.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void refund_notSuccess_isConflict() {
        tx.setStatus(PaymentStatus.PENDING);

        assertThatThrownBy(() -> service.refund(adminId, tx.getId(), new RefundPaymentRequest("x", false)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.CONFLICT);
    }
}
