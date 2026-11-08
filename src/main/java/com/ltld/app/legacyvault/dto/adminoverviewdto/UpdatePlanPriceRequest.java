package com.ltld.app.legacyvault.dto.adminoverviewdto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * C1: Admin chỉ đổi tên hiển thị và giá cho các lần mua SAU. Giao dịch đã tạo giữ nguyên số tiền
 * (PaymentTransaction.amountVnd), gói đang chạy không bị ảnh hưởng.
 */
public record UpdatePlanPriceRequest(
        @NotNull(message = "Price is required")
        @Min(value = 10_000, message = "Price must be at least 10,000 VND")
        @Max(value = 100_000_000, message = "Price must be at most 100,000,000 VND") Long priceVnd,
        @Size(max = 100, message = "Name must be at most 100 characters") String name,
        @Size(max = 255, message = "Reason must be at most 255 characters") String reason) {
}
