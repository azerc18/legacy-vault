package com.ltld.app.legacyvault.dto.auditdto;

import com.ltld.app.legacyvault.enums.SecurityAlertRule;
import com.ltld.app.legacyvault.enums.SecurityAlertSeverity;
import com.ltld.app.legacyvault.enums.SecurityAlertStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class SecurityAlertResponse {
    private UUID id;
    private SecurityAlertRule rule;
    private SecurityAlertSeverity severity;
    private SecurityAlertStatus status;
    private String subject;
    private String message;
    private int eventCount;
    private Instant firstSeenAt;
    private Instant lastSeenAt;
    private UUID acknowledgedBy;
    private Instant acknowledgedAt;
    private Instant createdAt;
}