package com.ltld.app.legacyvault.service.auditservice;

import com.ltld.app.legacyvault.entity.VaultActivityLog;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.ActionType;
import com.ltld.app.legacyvault.repository.VaultActivityLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VaultActivityLogServiceImpl implements VaultActivityLogService {

    private final VaultActivityLogRepository vaultActivityLogRepository;

    @Override
    public void logAction(UUID userId, UUID vaultId, ActionType actionType, String description, String ipAddress) {
        User user = new User();
        user.setId(userId);

        Vault vault = null;
        if (vaultId != null) {
            vault = new Vault();
            vault.setId(vaultId);
        }

        VaultActivityLog log = VaultActivityLog.builder()
                .user(user)
                .vault(vault)
                .actionType(actionType)
                .description(description)
                .ipAddress(ipAddress)
                .build();

        vaultActivityLogRepository.save(log);
    }

    @Override
    public List<VaultActivityLog> getUserLogs(UUID userId) {
        return vaultActivityLogRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }
}
