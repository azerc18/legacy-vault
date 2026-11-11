package com.ltld.app.legacyvault.service.paymentservice;

import com.ltld.app.legacyvault.entity.Subscription;
import com.ltld.app.legacyvault.entity.SubscriptionPlan;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.PlanFeature;
import com.ltld.app.legacyvault.enums.SubscriptionStatus;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.exception.PlanLimitReachedException;
import com.ltld.app.legacyvault.repository.LegacyMessageRepository;
import com.ltld.app.legacyvault.repository.LegalDocumentRepository;
import com.ltld.app.legacyvault.repository.SubscriptionPlanRepository;
import com.ltld.app.legacyvault.repository.SubscriptionRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.utility.EmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionOperations;

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
    @Mock private LegalDocumentRepository legalDocumentRepository;
    @Mock private LegacyMessageRepository legacyMessageRepository;
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
                vaultRepository,
                legalDocumentRepository,
                legacyMessageRepository
        );
        expiryScheduler = new SubscriptionExpiryScheduler(
                subscriptionRepository,
                emailSender,
                mock(com.ltld.app.legacyvault.service.notificationservice.InAppNotifier.class),
                TransactionOperations.withoutTransaction()
        );

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
        // N12: báo Owner sau khi gói đã chuyển EXPIRED
        verify(emailSender).sendSubscriptionExpiredNotice(eq("owner@example.com"), eq("Owner User"), any(), eq("VIP_MONTH"));

        // Khi tạo vault mới mà đã có >= 1 vault -> ném PlanLimitReachedException (403 PLAN_LIMIT_REACHED)
        when(subscriptionRepository.findFirstByUserIdAndStatusOrderByExpiresAtDesc(owner.getId(), SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(subscriptionPlanRepository.findByCode("FREE")).thenReturn(Optional.of(freePlan));
        when(vaultRepository.countByOwnerId(owner.getId())).thenReturn(1L);

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
        when(vaultRepository.countByOwnerId(owner.getId())).thenReturn(1L);
        when(legalDocumentRepository.sumSizeBytesByOwnerId(owner.getId())).thenReturn(100_000_000L);

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
        when(legalDocumentRepository.sumSizeBytesByOwnerId(owner.getId())).thenReturn(10_000_000L);
        assertDoesNotThrow(() -> entitlementService.assertStorageQuota(owner.getId(), 40_000_000L)); // 10 + 40 = 50MB <= 52.4MB
        PlanLimitReachedException storageEx = assertThrows(
                PlanLimitReachedException.class,
                () -> entitlementService.assertStorageQuota(owner.getId(), 50_000_000L) // 10 + 50 = 60MB > 52.4MB
        );
        assertEquals(ErrorCode.PLAN_LIMIT_REACHED, storageEx.getErrorCode());
        assertEquals(PlanFeature.STORAGE, storageEx.getFeature());
    }

    // m7: dung lượng đã dùng gồm cả đính kèm lời nhắn di nguyện (F4a), không chỉ tài liệu pháp lý
    @Test
    void assertStorageQuota_countsLegacyMessageAttachments() {
        when(subscriptionRepository.findFirstByUserIdAndStatusOrderByExpiresAtDesc(owner.getId(), SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(subscriptionPlanRepository.findByCode("FREE")).thenReturn(Optional.of(freePlan));
        when(legalDocumentRepository.sumSizeBytesByOwnerId(owner.getId())).thenReturn(20_000_000L);
        when(legacyMessageRepository.sumAttachmentSizeByOwnerId(owner.getId())).thenReturn(30_000_000L);

        // 20MB tài liệu + 30MB đính kèm = 50MB; thêm 2MB vẫn <= 52.4MB, thêm 3MB thì vượt
        assertDoesNotThrow(() -> entitlementService.assertStorageQuota(owner.getId(), 2_000_000L));
        PlanLimitReachedException ex = assertThrows(
                PlanLimitReachedException.class,
                () -> entitlementService.assertStorageQuota(owner.getId(), 3_000_000L)
        );
        assertEquals(PlanFeature.STORAGE, ex.getFeature());
    }

    // F4a: lời nhắn di nguyện theo cột legacy_messages_enabled của gói
    @Test
    void legacyMessages_freePlan_isBlockedWithLegacyMessagesFeature() {
        when(subscriptionRepository.findFirstByUserIdAndStatusOrderByExpiresAtDesc(owner.getId(), SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(subscriptionPlanRepository.findByCode("FREE")).thenReturn(Optional.of(freePlan));

        PlanLimitReachedException ex = assertThrows(PlanLimitReachedException.class,
                () -> entitlementService.assertCanUseLegacyMessages(owner.getId()));
        assertEquals(ErrorCode.PLAN_LIMIT_REACHED, ex.getErrorCode());
        assertEquals(PlanFeature.LEGACY_MESSAGES, ex.getFeature());
    }

    @Test
    void legacyMessages_activeVipPlan_isAllowed() {
        Subscription vipSub = Subscription.builder()
                .user(owner).plan(vipPlan).status(SubscriptionStatus.ACTIVE)
                .startedAt(LocalDateTime.now().minusDays(1)).expiresAt(LocalDateTime.now().plusDays(30))
                .build();
        when(subscriptionRepository.findFirstByUserIdAndStatusOrderByExpiresAtDesc(owner.getId(), SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(vipSub));

        assertDoesNotThrow(() -> entitlementService.assertCanUseLegacyMessages(owner.getId()));
    }

    @Test
    void legacyMessages_paidPlanWithColumnDisabled_isBlocked() {
        // Quyết định theo cột legacy_messages_enabled, không theo việc gói có phải VIP hay không
        SubscriptionPlan paidNoMedia = SubscriptionPlan.builder()
                .code("VIP_MONTH").name("VIP Monthly").maxVaults(5).maxBeneficiaries(10)
                .storageQuotaBytes(5_368_709_120L).legacyMessagesEnabled(false).build();
        Subscription sub = Subscription.builder()
                .user(owner).plan(paidNoMedia).status(SubscriptionStatus.ACTIVE)
                .startedAt(LocalDateTime.now().minusDays(1)).expiresAt(LocalDateTime.now().plusDays(30))
                .build();
        when(subscriptionRepository.findFirstByUserIdAndStatusOrderByExpiresAtDesc(owner.getId(), SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(sub));

        PlanLimitReachedException ex = assertThrows(PlanLimitReachedException.class,
                () -> entitlementService.assertCanUseLegacyMessages(owner.getId()));
        assertEquals(PlanFeature.LEGACY_MESSAGES, ex.getFeature());
    }

    // m2: DB thiếu dòng FREE -> không ném IllegalStateException, log.warn và áp hạn mức FREE mặc định
    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void missingFreePlanRow_logsWarningAndAppliesDefaultFreeLimits(CapturedOutput output) {
        when(subscriptionRepository.findFirstByUserIdAndStatusOrderByExpiresAtDesc(owner.getId(), SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(subscriptionPlanRepository.findByCode("FREE")).thenReturn(Optional.empty());

        SubscriptionPlan plan = entitlementService.resolveEffectivePlan(owner.getId());
        assertEquals("FREE", plan.getCode());
        assertFalse(plan.isVip());
        assertEquals(1, plan.getMaxVaults());
        assertEquals(2, plan.getMaxBeneficiaries());
        assertEquals(50L * 1024 * 1024, plan.getStorageQuotaBytes());
        assertFalse(plan.isLegacyMessagesEnabled());
        assertThat(output).contains("subscription plan FREE is missing");

        // 1 vault
        when(vaultRepository.countByOwnerId(owner.getId())).thenReturn(0L, 1L);
        assertDoesNotThrow(() -> entitlementService.assertCanCreateVault(owner.getId()));
        assertThrows(PlanLimitReachedException.class, () -> entitlementService.assertCanCreateVault(owner.getId()));

        // 2 người thụ hưởng
        assertDoesNotThrow(() -> entitlementService.assertCanAddBeneficiaries(owner.getId(), 2));
        assertThrows(PlanLimitReachedException.class, () -> entitlementService.assertCanAddBeneficiaries(owner.getId(), 3));

        // 50 MB
        when(legalDocumentRepository.sumSizeBytesByOwnerId(owner.getId())).thenReturn(0L);
        assertDoesNotThrow(() -> entitlementService.assertStorageQuota(owner.getId(), 50L * 1024 * 1024));
        assertThrows(PlanLimitReachedException.class,
                () -> entitlementService.assertStorageQuota(owner.getId(), 50L * 1024 * 1024 + 1));

        // Tắt tính năng VIP
        assertThrows(PlanLimitReachedException.class, () -> entitlementService.assertCanUseAdvancedAllocation(owner.getId()));
        assertThrows(PlanLimitReachedException.class, () -> entitlementService.assertCanUseTrustedConfirmers(owner.getId()));
        assertThrows(PlanLimitReachedException.class, () -> entitlementService.assertCanUseLegacyMessages(owner.getId()));
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
