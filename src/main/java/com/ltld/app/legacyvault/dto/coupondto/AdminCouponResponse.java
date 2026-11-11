package com.ltld.app.legacyvault.dto.coupondto;

import com.ltld.app.legacyvault.entity.Coupon;

import java.time.LocalDateTime;
import java.util.UUID;

public record AdminCouponResponse(UUID id, String code, int percentOff, String planCode, Integer maxRedemptions,
                                  int redeemedCount, LocalDateTime validUntil, boolean active, boolean expired,
                                  String note, LocalDateTime createdAt) {

    public static AdminCouponResponse from(Coupon c, LocalDateTime now) {
        boolean expired = c.getValidUntil() != null && !c.getValidUntil().isAfter(now);
        return new AdminCouponResponse(c.getId(), c.getCode(), c.getPercentOff(), c.getPlanCode(),
                c.getMaxRedemptions(), c.getRedeemedCount(), c.getValidUntil(), c.isActive(), expired,
                c.getNote(), c.getCreatedAt());
    }
}
