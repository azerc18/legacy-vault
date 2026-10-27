package com.ltld.app.legacyvault.dto.dmsdto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

// FR-03: Owner cấu hình Dead Man's Switch. Kênh thông báo chưa cho chọn (mặc định EMAIL).
@Data
public class DmsConfigRequest {
    @NotNull(message = "Check-in interval is required")
    @Min(value = 1, message = "Check-in interval must be at least 1 day")
    @Max(value = 365, message = "Check-in interval must be at most 365 days")
    private Integer checkinIntervalDays;

    @NotNull(message = "Grace period is required")
    @Min(value = 1, message = "Grace period must be at least 1 day")
    @Max(value = 90, message = "Grace period must be at most 90 days")
    private Integer gracePeriodDays;

    @NotNull(message = "Reminder count is required")
    @Min(value = 1, message = "Reminder count must be at least 1")
    @Max(value = 10, message = "Reminder count must be at most 10")
    private Integer reminderCount;
}
