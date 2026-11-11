package com.ltld.app.legacyvault.service.paymentservice;

import com.ltld.app.legacyvault.dto.paymentdto.PlanUsageResponse;
import com.ltld.app.legacyvault.entity.Subscription;
import com.ltld.app.legacyvault.entity.SubscriptionPlan;
import com.ltld.app.legacyvault.enums.SubscriptionStatus;
import com.ltld.app.legacyvault.repository.LegacyMessageRepository;
import com.ltld.app.legacyvault.repository.LegalDocumentRepository;
import com.ltld.app.legacyvault.repository.SubscriptionRepository;
import com.ltld.app.legacyvault.repository.VaultBeneficiaryRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlanUsageServiceTest {

    @Mock private EntitlementServiceImpl entitlementService;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private VaultRepository vaultRepository;
    @Mock private VaultBeneficiaryRepository vaultBeneficiaryRepository;
    @Mock private LegalDocumentRepository legalDocumentRepository;
    @Mock private LegacyMessageRepository legacyMessageRepository;

    private PlanUsageService service;
    private final UUID ownerId = UUID.randomUUID();
    private final SubscriptionPlan free = EntitlementServiceImpl.defaultFreePlan();
    private final SubscriptionPlan vip = SubscriptionPlan.builder().code("VIP_MONTH").maxVaults(5).maxBeneficiaries(10)
            .storageQuotaBytes(5L << 30).legacyMessagesEnabled(true).build();

    @BeforeEach
    void setUp() {
        service = new PlanUsageService(entitlementService, subscriptionRepository, vaultRepository,
                vaultBeneficiaryRepository, legalDocumentRepository, legacyMessageRepository);
    }

    private void usage(long vaults, List<Long> perVault, long docBytes, long msgBytes, long messages) {
        when(vaultRepository.countByOwnerId(ownerId)).thenReturn(vaults);
        when(vaultBeneficiaryRepository.countPerVaultByOwnerId(ownerId)).thenReturn(perVault);
        when(legalDocumentRepository.sumSizeBytesByOwnerId(ownerId)).thenReturn(docBytes);
        when(legacyMessageRepository.sumAttachmentSizeByOwnerId(ownerId)).thenReturn(msgBytes);
        when(legacyMessageRepository.countByOwnerId(ownerId)).thenReturn(messages);
    }

    @Test
    void vipOwner_withinLimits_hasNothingOver() {
        when(entitlementService.resolveEffectivePlan(ownerId)).thenReturn(vip);
        usage(2, List.of(3L, 1L), 10_000, 5_000, 4);

        PlanUsageResponse r = service.getUsage(ownerId);

        assertThat(r.isVip()).isTrue();
        assertThat(r.getVaultsUsed()).isEqualTo(2);
        assertThat(r.getMaxBeneficiariesInAVault()).isEqualTo(3);
        assertThat(r.getStorageUsedBytes()).isEqualTo(15_000);
        assertThat(r.getAtLimit()).isEmpty();
        assertThat(r.getOverLimit()).isEmpty();
        assertThat(r.isVipLapsed()).isFalse();
    }

    @Test
    void lapsedVip_overFreeLimits_listsEveryOverLimitFeature() {
        when(entitlementService.resolveEffectivePlan(ownerId)).thenReturn(free);
        LocalDateTime expired = LocalDateTime.of(2026, 11, 1, 8, 0);
        when(subscriptionRepository.findFirstByUserIdOrderByCreatedAtDesc(ownerId)).thenReturn(Optional.of(
                Subscription.builder().plan(vip).status(SubscriptionStatus.EXPIRED).expiresAt(expired).build()));
        usage(3, List.of(5L, 2L, 1L), 60L * 1024 * 1024, 0, 2);

        PlanUsageResponse r = service.getUsage(ownerId);

        assertThat(r.isVip()).isFalse();
        assertThat(r.isVipLapsed()).isTrue();
        assertThat(r.getVipExpiredAt()).isEqualTo(expired);
        assertThat(r.getOverLimit()).containsExactly("MULTIPLE_VAULTS", "BENEFICIARY_LIMIT", "STORAGE", "LEGACY_MESSAGES");
    }

    @Test
    void freeOwner_exactlyAtLimit_isAtLimitButNotOver() {
        when(entitlementService.resolveEffectivePlan(ownerId)).thenReturn(free);
        usage(1, List.of(2L), 0, 0, 0);

        PlanUsageResponse r = service.getUsage(ownerId);

        assertThat(r.getAtLimit()).containsExactly("MULTIPLE_VAULTS", "BENEFICIARY_LIMIT");
        assertThat(r.getOverLimit()).isEmpty();
        assertThat(r.isVipLapsed()).isFalse();
    }
}
