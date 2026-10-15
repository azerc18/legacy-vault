package com.ltld.app.legacyvault.service.auditservice;

import com.ltld.app.legacyvault.entity.AuditLog;
import com.ltld.app.legacyvault.repository.AuditLogRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class AuditEventListener {
    private final AuditLogRepository repository;
    private final UserRepository userRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMPLETION, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(AuditEvent e) {
        repository.save(AuditLog.builder()
                .actor(e.actorId() == null ? null : userRepository.getReferenceById(e.actorId()))
                .action(e.action())
                .vaultId(e.vaultId())
                .entityType(e.entityType())
                .entityId(e.entityId())
                .ipAddress(e.ipAddress())
                .metadata(e.metadata())
                .build());
    }
}
