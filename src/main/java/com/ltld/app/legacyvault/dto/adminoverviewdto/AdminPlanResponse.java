package com.ltld.app.legacyvault.dto.adminoverviewdto;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

/** C1: một gói thuê bao trên trang Quản lý gói, kèm số người đang dùng. */
@Getter
@Builder
public class AdminPlanResponse {
    private UUID id;
    private String code;
    private String name;
    private long priceVnd;
    private Integer durationDays;
    private int maxVaults;
    private int maxBeneficiaries;
    private long storageQuotaBytes;
    private boolean legacyMessagesEnabled;
    private long activeSubscribers;
}
