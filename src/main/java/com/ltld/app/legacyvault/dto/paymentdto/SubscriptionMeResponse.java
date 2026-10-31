package com.ltld.app.legacyvault.dto.paymentdto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionMeResponse {
    private String planCode;
    private String planName;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime expiresAt;
    private int maxVaults;
    private int maxBeneficiariesPerVault;
    private long maxStorageBytes;
    private boolean legacyMessageEnabled;
    private boolean vip;
    private List<SubscriptionPlanDto> availablePlans;
}
