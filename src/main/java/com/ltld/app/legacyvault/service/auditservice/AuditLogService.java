package com.ltld.app.legacyvault.service.auditservice;

import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;

import java.util.UUID;

public interface AuditLogService {
    void log(AuditAction action, AuditResult result, UUID userId, String email, UUID vaultId,
             String targetType, String targetId, String detail);

    default void log(AuditAction action, AuditResult result, UUID userId, String email,
                     String targetType, String targetId, String detail) {
        UUID vaultId = null;
        if ("Vault".equalsIgnoreCase(targetType) && targetId != null) {
            try {
                vaultId = UUID.fromString(targetId);
            } catch (IllegalArgumentException ignored) {
            }
        }
        log(action, result, userId, email, vaultId, targetType, targetId, detail);
    }

    default void success(AuditAction action, UUID userId, String email) {
        log(action, AuditResult.SUCCESS, userId, email, null, null, null, null);
    }

    default void failure(AuditAction action, UUID userId, String email, String detail) {
        log(action, AuditResult.FAILURE, userId, email, null, null, null, detail);
    }
}
