package com.ltld.app.legacyvault.dto.dmsdto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * F2: kết quả check-in một chạm. Chỉ trả mốc của chu kỳ mới: người gọi không đăng nhập,
 * nên không trả cấu hình đầy đủ (Executor, kênh nhắc...).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OneClickCheckinResponse {
    // Hạn check-in kế tiếp (lastCheckinAt + checkinIntervalDays)
    private LocalDateTime checkinDueAt;
    // Hạn chót trước khi DMS kích hoạt bàn giao
    private LocalDateTime triggerAt;
}
