package com.ltld.app.legacyvault.service.auditservice;

import com.ltld.app.legacyvault.enums.AuditAction;

import java.util.Map;
import java.util.UUID;

public record AuditEvent(AuditAction action, UUID actorId, String entityType,
                         UUID entityId, String ipAddress, Map<String, Object> metadata) {}
