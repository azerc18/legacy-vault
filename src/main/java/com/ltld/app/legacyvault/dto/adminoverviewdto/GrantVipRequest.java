package com.ltld.app.legacyvault.dto.adminoverviewdto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Việc 3: Admin tặng / bù VIP cho một Owner (cộng dồn vào hạn hiện tại). */
public record GrantVipRequest(
        @NotNull(message = "Days is required")
        @Min(value = 1, message = "Days must be at least 1")
        @Max(value = 365, message = "Days must be at most 365") Integer days,
        @NotBlank(message = "Reason is required")
        @Size(max = 255, message = "Reason must be at most 255 characters") String reason) {
}
