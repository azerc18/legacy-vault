package com.ltld.app.legacyvault.dto.notificationdto;

import com.ltld.app.legacyvault.enums.NotificationType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/** B7: một thông báo trong app của người đang đăng nhập; referenceType/referenceId để FE mở đúng màn hình. */
@Getter
@Builder
public class NotificationResponse {
    private final UUID id;
    private final NotificationType type;
    private final String content;
    private final String referenceType;
    private final UUID referenceId;
    private final boolean read;
    private final LocalDateTime createdAt;
}
