package com.ltld.app.legacyvault.dto.sessiondto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

// Một thiết bị / trình duyệt đang đăng nhập (= một refresh token còn hiệu lực).
// lastActiveAt: lần cấp token gần nhất (đăng nhập hoặc tự làm mới), nên gần với "lần cuối dùng".
@Getter
@Builder
public class UserSessionResponse {
    private UUID id;
    private String userAgent;
    private String ipAddress;
    private Instant lastActiveAt;
    private Instant expiresAt;
    private boolean current;
}
