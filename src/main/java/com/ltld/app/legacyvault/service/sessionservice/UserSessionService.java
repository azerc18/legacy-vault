package com.ltld.app.legacyvault.service.sessionservice;

import com.ltld.app.legacyvault.dto.sessiondto.UserSessionResponse;

import java.util.List;
import java.util.UUID;

// Thiết bị đang đăng nhập của chính người dùng (trang Hồ sơ).
// currentRawToken: refresh token trong cookie của trình duyệt đang gọi, có thể null.
public interface UserSessionService {
    List<UserSessionResponse> listActive(UUID userId, String currentRawToken);

    void revoke(UUID userId, UUID sessionId);

    // Đăng xuất mọi thiết bị khác, giữ lại thiết bị đang dùng. Trả số phiên đã thu hồi.
    int revokeOthers(UUID userId, String currentRawToken);
}
