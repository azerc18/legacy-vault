package com.ltld.app.legacyvault.dto.dmsdto;

import com.ltld.app.legacyvault.enums.DmsCheckinType;
import com.ltld.app.legacyvault.enums.NotificationChannel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** FR-03: một dòng lịch sử check-in / nhắc / hết hạn của Dead Man's Switch. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DmsCheckinResponse {
    private DmsCheckinType checkinType;
    // Chỉ có với REMINDER_SENT (kênh đã gửi nhắc)
    private NotificationChannel channel;
    private LocalDateTime occurredAt;
}
