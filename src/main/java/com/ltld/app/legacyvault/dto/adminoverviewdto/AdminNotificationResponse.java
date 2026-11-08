package com.ltld.app.legacyvault.dto.adminoverviewdto;

import com.ltld.app.legacyvault.enums.NotificationStatus;
import com.ltld.app.legacyvault.enums.NotificationType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/** B1: một email thông báo trên trang "Email gửi lỗi". Không trả nội dung email (có thể chứa link riêng tư). */
@Getter
@Builder
public class AdminNotificationResponse {
    private UUID id;
    private UUID recipientId;
    private String recipientEmail;
    private NotificationType type;
    private NotificationStatus status;
    private String referenceType;
    private UUID referenceId;
    private int attemptCount;
    private LocalDateTime lastAttemptAt;
    private LocalDateTime createdAt;
    private LocalDateTime sentAt;
}
