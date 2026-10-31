package com.ltld.app.legacyvault.dto.paymentdto;

import com.ltld.app.legacyvault.entity.SubscriptionPlan;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPlanDto {
    private String code;
    private String name;
    private long priceVnd;
    private Integer durationDays;
    private int maxVaults;
    private int maxBeneficiariesPerVault;
    private long maxStorageBytes;
    private boolean legacyMessageEnabled;

    public static SubscriptionPlanDto from(SubscriptionPlan plan) {
        return SubscriptionPlanDto.builder()
                .code(plan.getCode())
                .name(plan.getName())
                .priceVnd(plan.getPriceVnd())
                .durationDays(plan.getDurationDays())
                .maxVaults(plan.getMaxVaults())
                .maxBeneficiariesPerVault(plan.getMaxBeneficiaries())
                .maxStorageBytes(plan.getStorageQuotaBytes())
                .legacyMessageEnabled(plan.isLegacyMessagesEnabled())
                .build();
    }
}
