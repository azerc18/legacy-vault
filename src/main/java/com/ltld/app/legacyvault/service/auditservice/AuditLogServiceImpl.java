package com.ltld.app.legacyvault.service.auditservice;

import com.ltld.app.legacyvault.entity.AuditLog;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.ActionType;
import com.ltld.app.legacyvault.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Override
    public void logAction(UUID userId, UUID vaultId, ActionType actionType, String description, String ipAddress) {
        // Tạo vỏ bọc User (chỉ cần ID là Spring Data tự hiểu để gán Khóa Ngoại)
        User user = new User();
        user.setId(userId);

        Vault vault = null;
        if (vaultId != null) {
            vault = new Vault();
            vault.setId(vaultId);
        }

        // Tạo cục dữ liệu Log
        AuditLog log = AuditLog.builder()
                .user(user)
                .vault(vault)
                .actionType(actionType)
                .description(description)
                .ipAddress(ipAddress)
                .build();

        auditLogRepository.save(log);
    }

    @Override
    public List<AuditLog> getUserLogs(UUID userId) {
        return auditLogRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }
}
