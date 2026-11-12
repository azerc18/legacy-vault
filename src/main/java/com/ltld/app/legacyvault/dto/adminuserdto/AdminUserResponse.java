package com.ltld.app.legacyvault.dto.adminuserdto;

import com.ltld.app.legacyvault.enums.DmsStatus;
import com.ltld.app.legacyvault.enums.UserStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class AdminUserResponse {
    private UUID id;
    private String email;
    private String fullName;
    private UserStatus status;
    private List<String> roles;
    private Instant createdAt;
    private Instant lastLoginAt;
    private DmsStatus dmsStatus;
    private boolean executorRescueNeeded;
    // Khoá tạm do sai mật khẩu / mã 2FA (trạng thái vẫn ACTIVE): còn hiệu lực tới lúc này; null = không bị khoá tạm
    private Instant lockedUntil;
}