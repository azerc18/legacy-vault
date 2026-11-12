package com.ltld.app.legacyvault.dto.dmsdto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Kết quả Admin nhắc Owner check-in DMS. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDmsReminderResponse {
    private LocalDateTime sentAt;
    // false: chuông đã ghi nhưng email gửi lỗi; lượt nhắc được trả lại để Admin bấm lại ngay
    private boolean emailSent;
    // Lần sớm nhất được nhắc lại Owner này; null = được nhắc ngay
    private LocalDateTime nextAllowedAt;
    private LocalDateTime checkinDueAt;
    private LocalDateTime triggerAt;
}
