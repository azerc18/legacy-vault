package com.ltld.app.legacyvault.service.paymentservice;

import com.ltld.app.legacyvault.entity.SubscriptionPlan;
import com.ltld.app.legacyvault.repository.SubscriptionPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * T1: Khởi tạo các gói cước mặc định (FREE, VIP_MONTH, VIP_YEAR) theo bản cập nhật 31/10:
 * - FREE: 1 Master Vault, tối đa 2 người thụ hưởng, 50MB.
 * - VIP: tối đa 5 Vault, tối đa 10 người thụ hưởng (+ dự phòng & chia %), 5GB.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class SubscriptionPlanSeeder {

    private final SubscriptionPlanRepository subscriptionPlanRepository;

    @Bean
    public CommandLineRunner initSubscriptionPlans() {
        return args -> seedPlans();
    }

    public void seedPlans() {
        List<SubscriptionPlan> defaults = List.of(
                SubscriptionPlan.builder()
                        .code(SubscriptionPlan.CODE_FREE)
                        .name("Free Plan")
                        .priceVnd(0L)
                        .durationDays(null)
                        .maxVaults(EntitlementServiceImpl.FREE_MAX_VAULTS)
                        .maxBeneficiaries(EntitlementServiceImpl.FREE_MAX_BENEFICIARIES)
                        .storageQuotaBytes(EntitlementServiceImpl.FREE_MAX_STORAGE_BYTES) // 50 MB
                        .legacyMessagesEnabled(false)
                        .build(),
                SubscriptionPlan.builder()
                        .code(SubscriptionPlan.CODE_VIP_MONTH)
                        .name("VIP Monthly")
                        .priceVnd(99_000L)
                        .durationDays(30)
                        .maxVaults(EntitlementServiceImpl.VIP_MAX_VAULTS)
                        .maxBeneficiaries(EntitlementServiceImpl.VIP_MAX_BENEFICIARIES)
                        .storageQuotaBytes(EntitlementServiceImpl.VIP_MAX_STORAGE_BYTES) // 5 GB
                        .legacyMessagesEnabled(true)
                        .build(),
                SubscriptionPlan.builder()
                        .code(SubscriptionPlan.CODE_VIP_YEAR)
                        .name("VIP Yearly")
                        .priceVnd(990_000L)
                        .durationDays(365)
                        .maxVaults(EntitlementServiceImpl.VIP_MAX_VAULTS)
                        .maxBeneficiaries(EntitlementServiceImpl.VIP_MAX_BENEFICIARIES)
                        .storageQuotaBytes(EntitlementServiceImpl.VIP_MAX_STORAGE_BYTES) // 5 GB
                        .legacyMessagesEnabled(true)
                        .build()
        );

        for (SubscriptionPlan plan : defaults) {
            if (subscriptionPlanRepository.findByCode(plan.getCode()).isEmpty()) {
                subscriptionPlanRepository.save(plan);
                log.info("Seeded subscription plan: {}", plan.getCode());
            }
        }
    }
}
