package com.ltld.app.legacyvault.service.paymentservice;

import com.ltld.app.legacyvault.entity.Subscription;
import com.ltld.app.legacyvault.entity.SubscriptionPlan;
import com.ltld.app.legacyvault.enums.PlanFeature;
import com.ltld.app.legacyvault.enums.SubscriptionStatus;
import com.ltld.app.legacyvault.exception.PlanLimitReachedException;
import com.ltld.app.legacyvault.repository.SubscriptionPlanRepository;
import com.ltld.app.legacyvault.repository.SubscriptionRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * T1: Thực thi kiểm tra quyền lợi gói cước (FREE / VIP) theo bản cập nhật 9 điểm (31/10):
 * - FREE: 1 Master Vault, tối đa 2 người thụ hưởng/vault, 50MB lưu trữ, chỉ phân bổ EXCLUSIVE cơ bản.
 * - VIP: Đa Vault (tối đa 5 vault), tối đa 10 người thụ hưởng/vault, 5GB lưu trữ,
 *        hỗ trợ chia tỷ lệ % (PROPORTIONAL) + người nhận dự phòng (contingentBeneficiary),
 *        xác nhận chéo DMS (X2) và lời nhắn di nguyện (F4a).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EntitlementServiceImpl implements EntitlementService {

    public static final String PLAN_FREE = SubscriptionPlan.CODE_FREE;
    public static final int FREE_MAX_VAULTS = 1;
    public static final int FREE_MAX_BENEFICIARIES = 2;
    public static final long FREE_MAX_STORAGE_BYTES = 52_428_800L; // 50 MB
    public static final int VIP_MAX_VAULTS = 5;
    public static final int VIP_MAX_BENEFICIARIES = 10;
    public static final long VIP_MAX_STORAGE_BYTES = 5_368_709_120L; // 5 GB

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final VaultRepository vaultRepository;

    @Override
    public void assertCanCreateVault(UUID ownerId) {
        if (ownerId == null) {
            return;
        }
        SubscriptionPlan plan = resolveEffectivePlan(ownerId);
        int currentVaults = vaultRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId).size();
        if (currentVaults >= plan.getMaxVaults()) {
            throw new PlanLimitReachedException(
                    PlanFeature.MULTIPLE_VAULTS,
                    "Your current plan (" + plan.getCode() + ") allows at most " + plan.getMaxVaults()
                            + " vault(s). Upgrade to VIP to create multiple specialized vaults.");
        }
    }

    @Override
    public void assertCanAddBeneficiaries(UUID ownerId, int resultingCount) {
        if (ownerId == null || resultingCount <= 0) {
            return;
        }
        SubscriptionPlan plan = resolveEffectivePlan(ownerId);
        if (resultingCount > plan.getMaxBeneficiaries()) {
            throw new PlanLimitReachedException(
                    PlanFeature.BENEFICIARY_LIMIT,
                    "Your current plan (" + plan.getCode() + ") allows at most "
                            + plan.getMaxBeneficiaries()
                            + " beneficiaries per vault. Upgrade to VIP to add up to 10 beneficiaries.");
        }
    }

    @Override
    public void assertCanUseAdvancedAllocation(UUID ownerId) {
        if (ownerId == null) {
            return;
        }
        SubscriptionPlan plan = resolveEffectivePlan(ownerId);
        if (!plan.isVip()) {
            throw new PlanLimitReachedException(
                    PlanFeature.ADVANCED_ALLOCATION,
                    "Proportional (%) allocation and contingent beneficiaries require a VIP subscription.");
        }
    }

    @Override
    public void assertCanUseTrustedConfirmers(UUID ownerId) {
        if (ownerId == null) {
            return;
        }
        SubscriptionPlan plan = resolveEffectivePlan(ownerId);
        if (!plan.isVip()) {
            throw new PlanLimitReachedException(
                    PlanFeature.TRUSTED_CONFIRMERS,
                    "Trusted DMS confirmers require a VIP subscription.");
        }
    }

    @Override
    public void assertCanUseLegacyMessages(UUID ownerId) {
        if (ownerId == null) {
            return;
        }
        SubscriptionPlan plan = resolveEffectivePlan(ownerId);
        if (!plan.isLegacyMessagesEnabled()) {
            throw new PlanLimitReachedException(
                    PlanFeature.LEGACY_MESSAGES,
                    "Legacy audio and video messages are only available on the VIP plan.");
        }
    }

    @Override
    public void assertStorageQuota(UUID ownerId, long additionalBytes) {
        if (ownerId == null) {
            return;
        }
        SubscriptionPlan plan = resolveEffectivePlan(ownerId);
        if (additionalBytes > plan.getStorageQuotaBytes()) {
            throw new PlanLimitReachedException(
                    PlanFeature.STORAGE,
                    "Storage quota exceeded for plan " + plan.getCode()
                            + ". Upgrade to VIP for up to 5 GB of encrypted storage.");
        }
    }

    public SubscriptionPlan resolveEffectivePlan(UUID userId) {
        Optional<Subscription> activeSub = subscriptionRepository
                .findFirstByUserIdAndStatusOrderByExpiresAtDesc(userId, SubscriptionStatus.ACTIVE);
        if (activeSub.isPresent()) {
            Subscription sub = activeSub.get();
            if (sub.getExpiresAt() == null || sub.getExpiresAt().isAfter(LocalDateTime.now())) {
                if (sub.getPlan() != null) {
                    return sub.getPlan();
                }
            }
        }
        return subscriptionPlanRepository.findByCode(PLAN_FREE)
                .orElseGet(EntitlementServiceImpl::defaultFreePlan);
    }

    public static SubscriptionPlan defaultFreePlan() {
        return SubscriptionPlan.builder()
                .code(PLAN_FREE)
                .name("Free Plan")
                .priceVnd(0L)
                .durationDays(null)
                .maxVaults(FREE_MAX_VAULTS)
                .maxBeneficiaries(FREE_MAX_BENEFICIARIES)
                .storageQuotaBytes(FREE_MAX_STORAGE_BYTES)
                .legacyMessagesEnabled(false)
                .build();
    }
}
