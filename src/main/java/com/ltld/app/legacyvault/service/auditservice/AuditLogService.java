package com.ltld.app.legacyvault.service.auditservice;

import com.ltld.app.legacyvault.entity.AuditLog;
import com.ltld.app.legacyvault.enums.ActionType;

import java.util.List;
import java.util.UUID;

public interface AuditLogService {
    // Hàm này để các Service khác gọi ké vào khi muốn ghi Log
    void logAction(UUID userId, UUID vaultId, ActionType actionType, String description, String ipAddress);

    // Hàm này để Controller gọi khi Client muốn xem lịch sử
    List<AuditLog> getUserLogs(UUID userId);
}
