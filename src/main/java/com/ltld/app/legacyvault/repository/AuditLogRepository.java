package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

import java.util.UUID;

public interface AuditLogRepository extends Repository<AuditLog, UUID> {
    AuditLog save(AuditLog log);
    Page<AuditLog> findByActor_IdOrderByCreatedAtDesc(UUID actorId, Pageable pageable);

    @org.springframework.data.jpa.repository.Query(
        "SELECT a FROM AuditLog a " +
        "WHERE a.entityType = 'Vault' AND a.entityId = :vaultId " +
        "AND (cast(:startDate as timestamp) IS NULL OR a.createdAt >= :startDate) " +
        "AND (cast(:endDate as timestamp) IS NULL OR a.createdAt <= :endDate) " +
        "ORDER BY a.createdAt DESC"
    )
    Page<AuditLog> findVaultActivity(
        @org.springframework.data.repository.query.Param("vaultId") UUID vaultId,
        @org.springframework.data.repository.query.Param("startDate") java.time.Instant startDate,
        @org.springframework.data.repository.query.Param("endDate") java.time.Instant endDate,
        Pageable pageable
    );
}
