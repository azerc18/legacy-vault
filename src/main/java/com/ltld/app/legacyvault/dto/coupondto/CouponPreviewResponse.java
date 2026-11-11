package com.ltld.app.legacyvault.dto.coupondto;

import java.time.LocalDate;
import java.util.List;

/**
 * Việc 4: kết quả kiểm tra mã ở trang Bảng giá: % giảm và giá mới của từng gói VIP áp dụng được.
 * {@code validUntil} là ngày cuối cùng mã còn dùng được, theo giờ VN.
 */
public record CouponPreviewResponse(String code, int percentOff, LocalDate validUntil, List<CouponQuote> plans) {
}
