package com.ltld.app.legacyvault.dto.auditdto;

import com.ltld.app.legacyvault.enums.AuditAction;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class VaultActivityLogResponse {
    private UUID id;
    private AuditAction action;
    private String actorName;
    private String description;
    private String ipAddress;
    private Instant createdAt;
}
