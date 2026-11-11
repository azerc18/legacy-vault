package com.ltld.app.legacyvault.dto.coupondto;

import com.ltld.app.legacyvault.entity.Coupon;
import com.ltld.app.legacyvault.service.paymentservice.CouponService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** {@code validUntil} là ngày cuối cùng mã còn dùng được, theo giờ VN (review #256 n2). */
public record AdminCouponResponse(UUID id, String code, int percentOff, String planCode, Integer maxRedemptions,
                                  int redeemedCount, LocalDate validUntil, boolean active, boolean expired,
                                  String note, LocalDateTime createdAt) {

    public static AdminCouponResponse from(Coupon c, LocalDateTime now) {
        boolean expired = c.getValidUntil() != null && !c.getValidUntil().isAfter(now);
        return new AdminCouponResponse(c.getId(), c.getCode(), c.getPercentOff(), c.getPlanCode(),
                c.getMaxRedemptions(), c.getRedeemedCount(), CouponService.lastValidDay(c.getValidUntil()), c.isActive(), expired,
                c.getNote(), c.getCreatedAt());
    }
}
