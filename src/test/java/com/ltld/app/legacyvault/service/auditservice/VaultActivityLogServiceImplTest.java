package com.ltld.app.legacyvault.service.auditservice;

import com.ltld.app.legacyvault.entity.VaultActivityLog;
import com.ltld.app.legacyvault.enums.ActionType;
import com.ltld.app.legacyvault.repository.VaultActivityLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VaultActivityLogServiceImplTest {

    @Mock
    private VaultActivityLogRepository vaultActivityLogRepository;

    @InjectMocks
    private VaultActivityLogServiceImpl vaultActivityLogService;

    @Test
    void logAction_SavesLogSuccessfully() {
        UUID userId = UUID.randomUUID();
        UUID vaultId = UUID.randomUUID();
        ActionType actionType = ActionType.CREATE_VAULT;
        String description = "Test log";
        String ipAddress = "127.0.0.1";

        vaultActivityLogService.logAction(userId, vaultId, actionType, description, ipAddress);

        ArgumentCaptor<VaultActivityLog> logCaptor = ArgumentCaptor.forClass(VaultActivityLog.class);
        verify(vaultActivityLogRepository, times(1)).save(logCaptor.capture());

        VaultActivityLog savedLog = logCaptor.getValue();
        assertThat(savedLog.getUser().getId()).isEqualTo(userId);
        assertThat(savedLog.getVault().getId()).isEqualTo(vaultId);
        assertThat(savedLog.getActionType()).isEqualTo(actionType);
        assertThat(savedLog.getDescription()).isEqualTo(description);
        assertThat(savedLog.getIpAddress()).isEqualTo(ipAddress);
    }

    @Test
    void getUserLogs_ReturnsListOfLogs() {
        UUID userId = UUID.randomUUID();
        VaultActivityLog log1 = new VaultActivityLog();
        log1.setActionType(ActionType.CREATE_VAULT);
        
        when(vaultActivityLogRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(log1));

        List<VaultActivityLog> result = vaultActivityLogService.getUserLogs(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getActionType()).isEqualTo(ActionType.CREATE_VAULT);
        verify(vaultActivityLogRepository, times(1)).findByUserIdOrderByCreatedAtDesc(userId);
    }
}
