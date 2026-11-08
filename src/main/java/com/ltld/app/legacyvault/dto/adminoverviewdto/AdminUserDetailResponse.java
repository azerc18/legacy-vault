package com.ltld.app.legacyvault.dto.adminoverviewdto;

import com.ltld.app.legacyvault.dto.auditdto.AdminAuditLogResponse;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** A3: hồ sơ một người dùng cho ngăn kéo chi tiết của Admin. Không chứa nội dung tài sản. */
@Getter
@Builder
public class AdminUserDetailResponse {
    private UUID id;
    private String email;
    private String fullName;
    private String phoneNumber;
    private String status;
    private String kycLevel;
    private boolean mfaEnabled;
    private int failedLoginAttempts;
    private Instant lockedUntil;
    private Instant createdAt;
    private Instant lastLoginAt;
    private List<String> roles;

    private String dmsStatus;
    private LocalDateTime dmsNextCheckinDueAt;

    // Gói hiện tại (FREE nếu không có gói trả phí còn hạn)
    private String planCode;
    private String planName;
    private LocalDateTime planExpiresAt;

    private List<VaultItem> vaults;
    private List<AdminAuditLogResponse> recentLogs;
    private List<AlertItem> alerts;

    @Getter
    @Builder
    public static class VaultItem {
        private UUID id;
        private String name;
        private String status;
        private LocalDateTime claimDeadlineAt;
        private LocalDateTime createdAt;
    }

    @Getter
    @Builder
    public static class AlertItem {
        private UUID id;
        private String rule;
        private String severity;
        private String status;
        private String message;
        private Instant lastSeenAt;
    }
}
