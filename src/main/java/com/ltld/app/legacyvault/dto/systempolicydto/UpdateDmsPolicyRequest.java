package com.ltld.app.legacyvault.dto.systempolicydto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Admin lưu chính sách DMS. Kiểm min ≤ mặc định ≤ max và nằm trong giới hạn cứng ở
 * {@link com.ltld.app.legacyvault.service.systempolicyservice.DmsPolicyService} (cần so sánh nhiều ô với nhau).
 */
public record UpdateDmsPolicyRequest(@NotNull @Valid RangeInput checkinIntervalDays,
                                     @NotNull @Valid RangeInput gracePeriodDays,
                                     @NotNull @Valid RangeInput reminderCount,
                                     @NotNull Integer backupActivationWaitDays,
                                     // null = giữ giá trị đang lưu (client cũ chưa gửi ô này)
                                     Integer confirmerWaitHours) {

    public record RangeInput(@NotNull Integer min, @NotNull Integer defaultValue, @NotNull Integer max) {
    }
}
