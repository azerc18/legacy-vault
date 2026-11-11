package com.ltld.app.legacyvault.dto.coupondto;

import jakarta.validation.constraints.NotNull;

/** Việc 4: chỉ bật / tắt mã; mã và % giảm không đổi để lịch sử giao dịch luôn khớp. */
public record UpdateCouponRequest(@NotNull Boolean active) {
}
