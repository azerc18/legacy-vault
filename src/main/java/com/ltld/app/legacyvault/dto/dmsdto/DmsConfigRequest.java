package com.ltld.app.legacyvault.dto.dmsdto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

// FR-03: Owner cấu hình Dead Man's Switch. Kênh thông báo chưa cho chọn (mặc định EMAIL).
@Data
public class DmsConfigRequest {
    @NotNull(message = "Chu kỳ check-in không được để trống")
    @Min(value = 1, message = "Chu kỳ check-in phải từ 1 ngày trở lên")
    @Max(value = 365, message = "Chu kỳ check-in tối đa 365 ngày")
    private Integer checkinIntervalDays;

    @NotNull(message = "Thời gian chờ không được để trống")
    @Min(value = 1, message = "Thời gian chờ phải từ 1 ngày trở lên")
    @Max(value = 90, message = "Thời gian chờ tối đa 90 ngày")
    private Integer gracePeriodDays;

    @NotNull(message = "Số lần nhắc không được để trống")
    @Min(value = 1, message = "Số lần nhắc phải từ 1 trở lên")
    @Max(value = 10, message = "Số lần nhắc tối đa 10")
    private Integer reminderCount;
}
