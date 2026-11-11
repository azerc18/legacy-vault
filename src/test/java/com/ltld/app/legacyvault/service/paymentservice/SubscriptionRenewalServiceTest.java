package com.ltld.app.legacyvault.service.paymentservice;

import com.ltld.app.legacyvault.dto.paymentdto.SubscriptionMeResponse;
import com.ltld.app.legacyvault.entity.Subscription;
import com.ltld.app.legacyvault.entity.SubscriptionPlan;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.SubscriptionStatus;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.repository.SubscriptionRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.notificationservice.InAppNotifier;
import com.ltld.app.legacyvault.utility.EmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionOperations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Việc 6: hủy gia hạn cuối kỳ, bật lại, và mail nhắc có nút "Gia hạn 1 chạm". */
@ExtendWith(MockitoExtension.class)
class SubscriptionRenewalServiceTest {

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private PaymentService paymentService;
    @Mock private EmailSender emailSender;
    @Mock private InAppNotifier inAppNotifier;

    private SubscriptionRenewalService service;
    private User owner;
    private SubscriptionPlan vip;

    @BeforeEach
    void setUp() {
        service = new SubscriptionRenewalService(subscriptionRepository, userRepository, auditLogService, paymentService);
        owner = User.builder().id(UUID.randomUUID()).email("owner@example.com").fullName("Owner").build();
        vip = SubscriptionPlan.builder().code("VIP_MONTH").name("VIP Monthly").durationDays(30).build();
    }

    private Subscription running(LocalDateTime expiresAt) {
        return Subscription.builder().id(UUID.randomUUID()).user(owner).plan(vip).status(SubscriptionStatus.ACTIVE)
                .startedAt(LocalDateTime.now().minusDays(10)).expiresAt(expiresAt).build();
    }

    private void givenActive(Subscription sub) {
        when(subscriptionRepository.findFirstByUserIdAndStatusOrderByExpiresAtDesc(owner.getId(), SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.ofNullable(sub));
    }

    @Test
    void cancel_keepsVipUntilExpiryAndAudits() {
        LocalDateTime expiry = LocalDateTime.now().plusDays(20);
        Subscription sub = running(expiry);
        givenActive(sub);
        when(paymentService.getMySubscription(owner.getId())).thenReturn(new SubscriptionMeResponse());

        service.cancelAtPeriodEnd(owner.getId());

        assertTrue(sub.isCancelAtPeriodEnd());
        assertNotNull(sub.getCancelledAt());
        assertEquals(SubscriptionStatus.ACTIVE, sub.getStatus());
        assertEquals(expiry, sub.getExpiresAt());
        verify(userRepository).findByIdForUpdate(owner.getId());
        verify(auditLogService).log(eq(AuditAction.SUBSCRIPTION_CANCELLED), any(), eq(owner.getId()), any(),
                eq("Subscription"), eq(sub.getId().toString()), anyString());
    }

    @Test
    void cancel_twice_isIdempotent() {
        Subscription sub = running(LocalDateTime.now().plusDays(20));
        LocalDateTime firstCancel = LocalDateTime.now().minusDays(1);
        sub.setCancelAtPeriodEnd(true);
        sub.setCancelledAt(firstCancel);
        givenActive(sub);

        service.cancelAtPeriodEnd(owner.getId());

        assertEquals(firstCancel, sub.getCancelledAt());
        verify(subscriptionRepository, never()).save(any());
        verify(auditLogService, never()).log(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void cancel_withoutRunningVip_conflict() {
        givenActive(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.cancelAtPeriodEnd(owner.getId()));
        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
    }

    @Test
    void cancel_whenActiveRowAlreadyPastExpiry_conflict() {
        givenActive(running(LocalDateTime.now().minusHours(1)));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.cancelAtPeriodEnd(owner.getId()));
        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
    }

    @Test
    void resume_clearsCancelFlag() {
        Subscription sub = running(LocalDateTime.now().plusDays(3));
        sub.setCancelAtPeriodEnd(true);
        sub.setCancelledAt(LocalDateTime.now().minusDays(1));
        givenActive(sub);

        service.resume(owner.getId());

        assertFalse(sub.isCancelAtPeriodEnd());
        assertNull(sub.getCancelledAt());
        verify(auditLogService).log(eq(AuditAction.SUBSCRIPTION_RESUMED), any(), eq(owner.getId()), any(),
                eq("Subscription"), eq(sub.getId().toString()), anyString());
    }

    @Test
    void reminder_forCancelledPlan_usesCancelledWordingAndRenewLink() {
        LocalDateTime now = LocalDateTime.now();
        Subscription sub = running(now.plusDays(5));
        sub.setCancelAtPeriodEnd(true);
        SubscriptionExpiryScheduler scheduler = new SubscriptionExpiryScheduler(
                subscriptionRepository, emailSender, inAppNotifier, TransactionOperations.withoutTransaction());
        when(subscriptionRepository.findByStatusAndExpiresAtBefore(SubscriptionStatus.ACTIVE, now)).thenReturn(List.of());
        when(subscriptionRepository.findByStatusAndReminderSentFalseAndExpiresAtBetween(
                eq(SubscriptionStatus.ACTIVE), eq(now), any())).thenReturn(List.of(sub));

        scheduler.processExpirationsAndReminders(now);

        assertTrue(sub.isReminderSent());
        verify(emailSender).sendSubscriptionExpiryReminder("owner@example.com", "Owner", "VIP Monthly",
                "VIP_MONTH", sub.getExpiresAt(), true);
    }
}
