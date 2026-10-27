package com.ltld.app.legacyvault.dto.auditdto;

import com.ltld.app.legacyvault.enums.AuditAction;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class AdminAuditLogResponse {
    private UUID id;
    private AuditAction action;
    private String result;
    private UUID actorId;
    private String actorEmail;
    private String entityType;
    private UUID entityId;
    private UUID vaultId;
    private String ipAddress;
    private String detail;
    private String userAgent;
    private Instant createdAt;
}
