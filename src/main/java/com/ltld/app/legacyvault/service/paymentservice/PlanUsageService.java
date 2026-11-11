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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
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

        List<String> overLimit = new ArrayList<>();
        check(vaults, plan.getMaxVaults(), PlanFeature.MULTIPLE_VAULTS, overLimit);
        check(busiestVault, plan.getMaxBeneficiaries(), PlanFeature.BENEFICIARY_LIMIT, overLimit);
        check(storage, plan.getStorageQuotaBytes(), PlanFeature.STORAGE, overLimit);
        if (!plan.isLegacyMessagesEnabled() && messages > 0) {
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
                .vipExpiredAt(lapsed ? toInstant(latest.get().getExpiresAt()) : null)
                .vaultsUsed(vaults)
                .maxVaults(plan.getMaxVaults())
                .maxBeneficiariesInAVault(busiestVault)
                .maxBeneficiariesPerVault(plan.getMaxBeneficiaries())
                .storageUsedBytes(storage)
                .maxStorageBytes(plan.getStorageQuotaBytes())
                .legacyMessages(messages)
                .legacyMessagesEnabled(plan.isLegacyMessagesEnabled())
                .overLimit(overLimit)
                .build();
    }

    // Giống NotificationParams (#247): LocalDateTime trong DB là giờ JVM, đổi sang Instant để FE tự đổi múi giờ
    private static Instant toInstant(LocalDateTime t) {
        return t == null ? null : t.atZone(ZoneId.systemDefault()).toInstant();
    }

    private static void check(long used, long max, PlanFeature feature, List<String> overLimit) {
        if (used > max) {
            overLimit.add(feature.name());
        }
    }
}
