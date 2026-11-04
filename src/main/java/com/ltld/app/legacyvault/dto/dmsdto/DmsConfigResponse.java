package com.ltld.app.legacyvault.dto.dmsdto;

import com.ltld.app.legacyvault.enums.DmsStatus;
import com.ltld.app.legacyvault.enums.NotificationChannel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** FR-03: cấu hình và lịch hiện tại của Dead Man's Switch. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DmsConfigResponse {
    // FR-03: false = Owner chưa cấu hình DMS; khi đó chỉ có hasUsableExecutor, các trường khác null.
    private Boolean configured;
    private DmsStatus status;
    private Integer checkinIntervalDays;
    private Integer gracePeriodDays;
    private Integer reminderCount;
    private NotificationChannel notificationChannel;
    private LocalDateTime lastCheckinAt;
    // Hạn check-in = lastCheckinAt + checkinIntervalDays
    private LocalDateTime checkinDueAt;
    // Thời điểm kích hoạt bàn giao nếu Owner không check-in (= DmsConfig.nextCheckinDueAt)
    private LocalDateTime triggerAt;
    private LocalDateTime nextReminderAt;
    private Integer remindersSent;
    private LocalDateTime triggeredAt;
    // FR-03: trigger lúc này có kích hoạt được Executor nào không (ExecutorActivationRule, cùng quy tắc với FR-07).
    // null khi DMS đã TRIGGERED: bàn giao đã bắt đầu nên câu hỏi này không còn ý nghĩa.
    // Chưa cấu hình (configured = false): có Executor chính chưa REVOKED không, tức điều kiện bật DMS lần đầu.
    private Boolean hasUsableExecutor;
}
