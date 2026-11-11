package com.ltld.app.legacyvault.dto.coupondto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Việc 4: admin tạo mã. Mã 3–40 ký tự chữ, số, gạch ngang hoặc gạch dưới (lưu chữ hoa). Giảm 1–90% (muốn tặng
 * miễn phí thì dùng "Tặng VIP"). {@code validUntil} tính hết ngày đó; {@code planCode} trống = mọi gói VIP.
 */
public record CreateCouponRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{3,40}", message = "Code must be 3-40 letters, digits, - or _")
        String code,
        @Min(1) @Max(90) int percentOff,
        @Size(max = 50) String planCode,
        @Min(1) @Max(100000) Integer maxRedemptions,
        LocalDate validUntil,
        @Size(max = 255) String note) {
}
