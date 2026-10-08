package com.ltld.app.legacyvault.service.auditservice;

import com.ltld.app.legacyvault.entity.VaultActivityLog;
import com.ltld.app.legacyvault.enums.ActionType;

import java.util.List;
import java.util.UUID;

public interface VaultActivityLogService {
    void logAction(UUID userId, UUID vaultId, ActionType actionType, String description, String ipAddress);
    List<VaultActivityLog> getUserLogs(UUID userId);
}
