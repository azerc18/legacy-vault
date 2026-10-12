package com.ltld.app.legacyvault.dev;

import java.time.LocalDateTime;
import java.util.List;

/** DTO của bộ công cụ test FR-07 (profile dev). */
public final class Fr07DevDtos {

    private Fr07DevDtos() {
    }

    /** nextCheckinDueInDays: âm = đã quá hạn (mặc định -1); dương = chưa đến hạn. */
    public record SeedRequest(Integer nextCheckinDueInDays, Boolean withPrimary, Boolean withBackup,
                              Integer backupWaitDays) {
    }

    public record SeedResponse(String ownerId, String ownerEmail, String primaryAssignmentId, String primaryEmail,
                               String backupAssignmentId, String backupEmail, String dmsConfigId,
                               LocalDateTime nextCheckinDueAt) {
    }

    public record ScanRequest(LocalDateTime now) {
    }

    public record MailFailureRequest(Boolean enabled) {
    }

    public record DmsView(String id, String status, LocalDateTime nextCheckinDueAt, LocalDateTime triggeredAt,
                          Integer backupActivationWaitDays) {
    }

    public record ExecutorView(String id, String executorType, String status, String email,
                               LocalDateTime activatedAt, LocalDateTime revokedAt, LocalDateTime acknowledgedAt) {
    }

    public record NotificationView(String recipientEmail, String type, String channel, String status,
                                   LocalDateTime sentAt, String content) {
    }

    public record AuditView(String action, String entityType, String entityId, String actorEmail, String metadata,
                            LocalDateTime createdAt) {
    }

    public record StateResponse(DmsView dms, List<ExecutorView> executors, List<NotificationView> notifications,
                                List<AuditView> audits, boolean mailFailureEnabled) {
    }
}
