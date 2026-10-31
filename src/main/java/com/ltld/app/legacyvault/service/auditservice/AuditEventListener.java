package com.ltld.app.legacyvault.service.auditservice;

import com.ltld.app.legacyvault.entity.AuditLog;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.repository.AuditLogRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditEvent;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class AuditEventListener {
    private final AuditLogRepository repository;
    private final UserRepository userRepository;

    // Commit thành công: ghi mọi sự kiện. fallbackExecution = true để sự kiện phát ngoài transaction vẫn được ghi.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCommit(AuditEvent e) {
        save(e);
    }

    // Rollback: chỉ ghi sự kiện FAILURE (ví dụ VAULT_DELETE_DENIED). SUCCESS bị bỏ vì việc đó chưa xảy ra.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRollback(AuditEvent e) {
        if (isFailure(e)) {
            save(e);
        }
    }

    static boolean isFailure(AuditEvent e) {
        return e.metadata() != null
                && AuditResult.FAILURE.name().equals(e.metadata().get("result"));
    }

    private void save(AuditEvent e) {
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