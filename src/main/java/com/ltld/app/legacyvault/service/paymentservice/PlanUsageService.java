package com.ltld.app.legacyvault.service.paymentservice;

import com.ltld.app.legacyvault.dto.paymentdto.PlanUsageResponse;
import com.ltld.app.legacyvault.entity.Subscription;
import com.ltld.app.legacyvault.entity.SubscriptionPlan;
import com.ltld.app.legacyvault.enums.PlanFeature;
import com.ltld.app.legacyvault.repository.LegacyMessageRepository;
import com.ltld.app.legacyvault.repository.LegalDocumentRepository;
import com.ltld.app.legacyvault.repository.SubscriptionRepository;
import com.ltld.app.legacyvault.repository.VaultBeneficiaryRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Việc 2: tính mức sử dụng của Owner theo đúng cách {@link EntitlementServiceImpl} đếm khi chặn tạo mới. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlanUsageService {

    private final EntitlementServiceImpl entitlementService;
    private final SubscriptionRepository subscriptionRepository;
    private final VaultRepository vaultRepository;
    private final VaultBeneficiaryRepository vaultBeneficiaryRepository;
    private final LegalDocumentRepository legalDocumentRepository;
    private final LegacyMessageRepository legacyMessageRepository;

    public PlanUsageResponse getUsage(UUID ownerId) {
        SubscriptionPlan plan = entitlementService.resolveEffectivePlan(ownerId);

        long vaults = vaultRepository.countByOwnerId(ownerId);
        long busiestVault = vaultBeneficiaryRepository.countPerVaultByOwnerId(ownerId).stream()
                .mapToLong(Long::longValue).max().orElse(0L);
        long storage = legalDocumentRepository.sumSizeBytesByOwnerId(ownerId)
                + legacyMessageRepository.sumAttachmentSizeByOwnerId(ownerId);
        long messages = legacyMessageRepository.countByOwnerId(ownerId);

        List<String> atLimit = new ArrayList<>();
        List<String> overLimit = new ArrayList<>();
        check(vaults, plan.getMaxVaults(), PlanFeature.MULTIPLE_VAULTS, atLimit, overLimit);
        check(busiestVault, plan.getMaxBeneficiaries(), PlanFeature.BENEFICIARY_LIMIT, atLimit, overLimit);
        check(storage, plan.getStorageQuotaBytes(), PlanFeature.STORAGE, atLimit, overLimit);
        if (!plan.isLegacyMessagesEnabled() && messages > 0) {
            atLimit.add(PlanFeature.LEGACY_MESSAGES.name());
            overLimit.add(PlanFeature.LEGACY_MESSAGES.name());
        }

        boolean vip = plan.isVip();
        Optional<Subscription> latest = vip ? Optional.empty()
                : subscriptionRepository.findFirstByUserIdOrderByCreatedAtDesc(ownerId);
        boolean lapsed = latest.map(s -> s.getPlan() != null && s.getPlan().isVip()).orElse(false);

        return PlanUsageResponse.builder()
                .planCode(plan.getCode())
                .vip(vip)
                .vipLapsed(lapsed)
                .vipExpiredAt(lapsed ? latest.get().getExpiresAt() : null)
                .vaultsUsed(vaults)
                .maxVaults(plan.getMaxVaults())
                .maxBeneficiariesInAVault(busiestVault)
                .maxBeneficiariesPerVault(plan.getMaxBeneficiaries())
                .storageUsedBytes(storage)
                .maxStorageBytes(plan.getStorageQuotaBytes())
                .legacyMessages(messages)
                .legacyMessagesEnabled(plan.isLegacyMessagesEnabled())
                .atLimit(atLimit)
                .overLimit(overLimit)
                .build();
    }

    private static void check(long used, long max, PlanFeature feature, List<String> atLimit, List<String> overLimit) {
        if (used >= max) {
            atLimit.add(feature.name());
        }
        if (used > max) {
            overLimit.add(feature.name());
        }
    }
}
