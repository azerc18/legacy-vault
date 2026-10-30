package com.ltld.app.legacyvault.service.auditservice;

import com.ltld.app.legacyvault.entity.AuditLog;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.repository.AuditLogRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditEvent;
import com.ltld.app.legacyvault.service.auditservice.AuditEventListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AuditEventListenerTest {
    @Mock
    private AuditLogRepository repository;
    @Mock private UserRepository userRepository;
    @InjectMocks
    private AuditEventListener listener;

    private static AuditEvent event(AuditResult r) {
        return new AuditEvent(AuditAction.VAULT_DELETED, null, UUID.randomUUID(), "Vault",
                UUID.randomUUID(), null, Map.of("result", r.name()));
    }

    @Test
    void onCommit_savesSuccess() {
        listener.onCommit(event(AuditResult.SUCCESS));
        verify(repository).save(any(AuditLog.class));
    }
    @Test void onRollback_skipsSuccess() {
        listener.onRollback(event(AuditResult.SUCCESS));
        verifyNoInteractions(repository);
    }
    @Test void onRollback_savesFailure() {
        listener.onRollback(event(AuditResult.FAILURE));
        verify(repository).save(any(AuditLog.class));
    }
}