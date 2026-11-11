package com.ltld.app.legacyvault.dto.coupondto;

import java.time.LocalDateTime;
import java.util.List;

/** Việc 4: kết quả kiểm tra mã ở trang Bảng giá: % giảm và giá mới của từng gói VIP áp dụng được. */
public record CouponPreviewResponse(String code, int percentOff, LocalDateTime validUntil, List<CouponQuote> plans) {
}
