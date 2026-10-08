package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

import java.util.UUID;

public interface AuditLogRepository extends Repository<AuditLog, UUID> {
    AuditLog save(AuditLog log);
    Page<AuditLog> findByActor_IdOrderByCreatedAtDesc(UUID actorId, Pageable pageable);
}
