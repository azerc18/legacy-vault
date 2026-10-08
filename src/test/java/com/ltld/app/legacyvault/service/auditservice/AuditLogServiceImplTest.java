package com.ltld.app.legacyvault.service.auditservice;

import com.ltld.app.legacyvault.entity.AuditLog;
import com.ltld.app.legacyvault.enums.ActionType;
import com.ltld.app.legacyvault.repository.AuditLogRepository;
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
class AuditLogServiceImplTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditLogServiceImpl auditLogService;

    @Test
    void logAction_SavesLogSuccessfully() {
        UUID userId = UUID.randomUUID();
        UUID vaultId = UUID.randomUUID();
        ActionType actionType = ActionType.CREATE_VAULT;
        String description = "Test log";
        String ipAddress = "127.0.0.1";

        // Chạy hàm thực tế
        auditLogService.logAction(userId, vaultId, actionType, description, ipAddress);

        // Kỹ thuật nâng cao: Dùng ArgumentCaptor để "tóm" lấy cái Object vừa bị đẩy vào hàm save()
        ArgumentCaptor<AuditLog> logCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(1)).save(logCaptor.capture());

        // Kiểm tra xem Object bị "tóm" có chứa đúng dữ liệu không
        AuditLog savedLog = logCaptor.getValue();
        assertThat(savedLog.getUser().getId()).isEqualTo(userId);
        assertThat(savedLog.getVault().getId()).isEqualTo(vaultId);
        assertThat(savedLog.getActionType()).isEqualTo(actionType);
        assertThat(savedLog.getDescription()).isEqualTo(description);
        assertThat(savedLog.getIpAddress()).isEqualTo(ipAddress);
    }

    @Test
    void getUserLogs_ReturnsListOfLogs() {
        UUID userId = UUID.randomUUID();
        AuditLog log1 = new AuditLog();
        log1.setActionType(ActionType.CREATE_VAULT);

        when(auditLogRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(log1));

        List<AuditLog> result = auditLogService.getUserLogs(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getActionType()).isEqualTo(ActionType.CREATE_VAULT);
        verify(auditLogRepository, times(1)).findByUserIdOrderByCreatedAtDesc(userId);
    }
}
