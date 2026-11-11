package com.ltld.app.legacyvault.service.paymentservice;

import com.ltld.app.legacyvault.entity.Subscription;
import com.ltld.app.legacyvault.entity.SubscriptionPlan;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Việc 4: dùng thử VIP một lần cho mỗi tài khoản. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TrialServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private SubscriptionPlanRepository planRepository;
    @Mock private PaymentTransactionRepository transactionRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private PaymentService paymentService;

    private TrialService service;
    private User owner;

    @BeforeEach
    void setUp() {
        service = new TrialService(userRepository, subscriptionRepository, planRepository, transactionRepository,
                auditLogService, paymentService, 7);
        owner = User.builder().id(UUID.randomUUID()).email("owner@example.com").build();
        when(userRepository.findByIdForUpdate(owner.getId())).thenReturn(Optional.of(owner));
        when(planRepository.findByCode("VIP_MONTH")).thenReturn(Optional.of(
                SubscriptionPlan.builder().code("VIP_MONTH").name("VIP Monthly").priceVnd(99_000L).build()));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> {
            Subscription s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });
    }

    @Test
    void newOwner_getsSevenDayVipTrialWithoutExpiringMail() {
        service.start(owner.getId());

        ArgumentCaptor<Subscription> saved = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(saved.capture());
        Subscription s = saved.getValue();
        assertThat(s.isTrial()).isTrue();
        assertThat(s.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(s.getPlan().getCode()).isEqualTo("VIP_MONTH");
        assertThat(Duration.between(s.getStartedAt(), s.getExpiresAt())).isEqualTo(Duration.ofDays(7));
        assertThat(s.isReminderSent()).isTrue();
        verify(auditLogService).log(eq(AuditAction.TRIAL_STARTED), any(), eq(owner.getId()), any(), any(), any(), any());
        verify(paymentService).getMySubscription(owner.getId());
    }

    @Test
    void ownerWhoEverHadAPlan_isNotEligible() {
        when(subscriptionRepository.existsByUserId(owner.getId())).thenReturn(true);
        assertThatThrownBy(() -> service.start(owner.getId()))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(ErrorCode.TRIAL_NOT_ELIGIBLE);
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void ownerWhoPaidBefore_isNotEligible() {
        when(transactionRepository.existsByUserIdAndStatus(owner.getId(), PaymentStatus.SUCCESS)).thenReturn(true);
        assertThatThrownBy(() -> service.start(owner.getId()))
                .extracting("errorCode").isEqualTo(ErrorCode.TRIAL_NOT_ELIGIBLE);
    }
}
