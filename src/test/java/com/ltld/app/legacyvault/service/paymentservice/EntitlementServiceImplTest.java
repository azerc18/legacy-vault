package com.ltld.app.legacyvault.service.paymentservice;

import com.ltld.app.legacyvault.entity.Subscription;
import com.ltld.app.legacyvault.entity.SubscriptionPlan;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.PlanFeature;
import com.ltld.app.legacyvault.enums.SubscriptionStatus;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.exception.PlanLimitReachedException;
import com.ltld.app.legacyvault.repository.SubscriptionPlanRepository;
import com.ltld.app.legacyvault.repository.SubscriptionRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.utility.EmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EntitlementServiceImplTest {

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private SubscriptionPlanRepository subscriptionPlanRepository;
    @Mock private VaultRepository vaultRepository;
    @Mock private EmailSender emailSender;

    private EntitlementServiceImpl entitlementService;
    private SubscriptionExpiryScheduler expiryScheduler;

    private User owner;
    private SubscriptionPlan freePlan;
    private SubscriptionPlan vipPlan;

    @BeforeEach
    void setUp() {
        entitlementService = new EntitlementServiceImpl(
                subscriptionRepository,
                subscriptionPlanRepository,
                vaultRepository
        );
        expiryScheduler = new SubscriptionExpiryScheduler(subscriptionRepository, emailSender);

        owner = User.builder()
                .id(UUID.randomUUID())
                .email("owner@example.com")
                .fullName("Owner User")
                .build();

        freePlan = SubscriptionPlan.builder()
                .code("FREE")
                .name("Free Plan")
                .maxVaults(1)
                .maxBeneficiaries(2)
                .storageQuotaBytes(52_428_800L)
                .legacyMessagesEnabled(false)
                .build();

        vipPlan = SubscriptionPlan.builder()
                .code("VIP_MONTH")
                .name("VIP Monthly")
                .maxVaults(5)
                .maxBeneficiaries(10)
                .storageQuotaBytes(5_368_709_120L)
                .legacyMessagesEnabled(true)
                .build();
    }

    // Case 4: hết hạn VIP -> đánh dấu EXPIRED, chặn tạo mới (PLAN_LIMIT_REACHED), không xoá dữ liệu / DMS vẫn chạy
    @Test
    void expiredSubscription_marksExpiredAndBlocksNewVaultCreationWithPlanLimitReached() {
        LocalDateTime now = LocalDateTime.now();
        Subscription expiredVipSub = Subscription.builder()
                .id(UUID.randomUUID())
                .user(owner)
                .plan(vipPlan)
                .status(SubscriptionStatus.ACTIVE)
                .startedAt(now.minusDays(31))
                .expiresAt(now.minusDays(1))
                .build();

        when(subscriptionRepository.findByStatusAndExpiresAtBefore(SubscriptionStatus.ACTIVE, now))
                .thenReturn(List.of(expiredVipSub));
        when(subscriptionRepository.findByStatusAndReminderSentFalseAndExpiresAtBetween(
                eq(SubscriptionStatus.ACTIVE), eq(now), any()))
                .thenReturn(List.of());

        // Chạy job hết hạn hàng ngày -> chuyển trạng thái sang EXPIRED, không đụng tới Vault hay DMS
        int expiredCount = expiryScheduler.processExpirationsAndReminders(now);
        assertEquals(1, expiredCount);
        assertEquals(SubscriptionStatus.EXPIRED, expiredVipSub.getStatus());
        verify(vaultRepository, never()).delete(any());

        // Khi tạo vault mới mà đã có >= 1 vault -> ném PlanLimitReachedException (403 PLAN_LIMIT_REACHED)
        when(subscriptionRepository.findFirstByUserIdAndStatusOrderByExpiresAtDesc(owner.getId(), SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(subscriptionPlanRepository.findByCode("FREE")).thenReturn(Optional.of(freePlan));
        when(vaultRepository.findByOwnerIdOrderByCreatedAtDesc(owner.getId()))
                .thenReturn(List.of(Vault.builder().id(UUID.randomUUID()).owner(owner).build()));

        PlanLimitReachedException ex = assertThrows(
                PlanLimitReachedException.class,
                () -> entitlementService.assertCanCreateVault(owner.getId())
        );
        assertEquals(ErrorCode.PLAN_LIMIT_REACHED, ex.getErrorCode());
        assertEquals(PlanFeature.MULTIPLE_VAULTS, ex.getFeature());
    }

    @Test
    void activeVipSubscription_allowsMultiVaultAdvancedAllocationTrustedConfirmersAndLegacyMessages() {
        Subscription activeVip = Subscription.builder()
                .id(UUID.randomUUID())
                .user(owner)
                .plan(vipPlan)
                .status(SubscriptionStatus.ACTIVE)
                .startedAt(LocalDateTime.now().minusDays(2))
                .expiresAt(LocalDateTime.now().plusDays(28))
                .build();

        when(subscriptionRepository.findFirstByUserIdAndStatusOrderByExpiresAtDesc(owner.getId(), SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(activeVip));
        when(vaultRepository.findByOwnerIdOrderByCreatedAtDesc(owner.getId()))
                .thenReturn(List.of(Vault.builder().id(UUID.randomUUID()).owner(owner).build()));

        assertDoesNotThrow(() -> entitlementService.assertCanCreateVault(owner.getId()));
        assertDoesNotThrow(() -> entitlementService.assertCanUseAdvancedAllocation(owner.getId()));
        assertDoesNotThrow(() -> entitlementService.assertCanUseTrustedConfirmers(owner.getId()));
        assertDoesNotThrow(() -> entitlementService.assertCanUseLegacyMessages(owner.getId()));
        assertDoesNotThrow(() -> entitlementService.assertStorageQuota(owner.getId(), 500_000_000L));
    }

    @Test
    void freePlan_blocksAdvancedAllocationTrustedConfirmersLegacyMessagesAndThirdBeneficiary() {
        when(subscriptionRepository.findFirstByUserIdAndStatusOrderByExpiresAtDesc(owner.getId(), SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(subscriptionPlanRepository.findByCode("FREE")).thenReturn(Optional.of(freePlan));

        PlanLimitReachedException advEx = assertThrows(PlanLimitReachedException.class,
                () -> entitlementService.assertCanUseAdvancedAllocation(owner.getId()));
        assertEquals(ErrorCode.PLAN_LIMIT_REACHED, advEx.getErrorCode());
        assertEquals(PlanFeature.ADVANCED_ALLOCATION, advEx.getFeature());

        PlanLimitReachedException tcEx = assertThrows(PlanLimitReachedException.class,
                () -> entitlementService.assertCanUseTrustedConfirmers(owner.getId()));
        assertEquals(ErrorCode.PLAN_LIMIT_REACHED, tcEx.getErrorCode());
        assertEquals(PlanFeature.TRUSTED_CONFIRMERS, tcEx.getFeature());

        PlanLimitReachedException lmEx = assertThrows(PlanLimitReachedException.class,
                () -> entitlementService.assertCanUseLegacyMessages(owner.getId()));
        assertEquals(ErrorCode.PLAN_LIMIT_REACHED, lmEx.getErrorCode());
        assertEquals(PlanFeature.LEGACY_MESSAGES, lmEx.getFeature());

        // Free cho phép tối đa 2 người thụ hưởng (resulting = 1, 2 -> ok; resulting = 3 -> ném lỗi)
        assertDoesNotThrow(() -> entitlementService.assertCanAddBeneficiaries(owner.getId(), 1));
        assertDoesNotThrow(() -> entitlementService.assertCanAddBeneficiaries(owner.getId(), 2));
        PlanLimitReachedException benEx = assertThrows(
                PlanLimitReachedException.class,
                () -> entitlementService.assertCanAddBeneficiaries(owner.getId(), 3)
        );
        assertEquals(ErrorCode.PLAN_LIMIT_REACHED, benEx.getErrorCode());
        assertEquals(PlanFeature.BENEFICIARY_LIMIT, benEx.getFeature());

        // Free tối đa 50MB (52_428_800 bytes)
        assertDoesNotThrow(() -> entitlementService.assertStorageQuota(owner.getId(), 50_000_000L));
        PlanLimitReachedException storageEx = assertThrows(
                PlanLimitReachedException.class,
                () -> entitlementService.assertStorageQuota(owner.getId(), 60_000_000L)
        );
        assertEquals(ErrorCode.PLAN_LIMIT_REACHED, storageEx.getErrorCode());
        assertEquals(PlanFeature.STORAGE, storageEx.getFeature());
    }

    @Test
    void planLimitReachedException_hasForbiddenStatusAndErrorCodeAndFeature() {
        PlanLimitReachedException ex = new PlanLimitReachedException(PlanFeature.BENEFICIARY_LIMIT, "Plan limit reached");
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PLAN_LIMIT_REACHED);
        assertThat(ex.getFeature()).isEqualTo(PlanFeature.BENEFICIARY_LIMIT);
        assertThat(ex.getMessage()).isEqualTo("Plan limit reached");
    }

    @Test
    void planLimitReachedException_nullFeature_throwsNullPointerException() {
        assertThatThrownBy(() -> new PlanLimitReachedException(null, "Plan limit reached"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("feature");
    }
}
