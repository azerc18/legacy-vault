package com.ltld.app.legacyvault.service.notificationservice;

import com.ltld.app.legacyvault.dto.notificationdto.NotificationResponse;
import org.springframework.data.domain.Page;

import java.util.UUID;

/** B7: thông báo trong app (channel IN_APP) của chính người đang đăng nhập, dùng chung cho mọi vai trò. */
public interface InAppNotificationService {

    Page<NotificationResponse> listMine(UUID userId, int page, int size);

    long countUnread(UUID userId);

    /** Thông báo của người khác hoặc không tồn tại -> 404. Đánh dấu lần 2 không đổi gì. */
    void markRead(UUID notificationId, UUID userId);
}
