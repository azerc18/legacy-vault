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
        value = "SELECT DISTINCT a FROM AuditLog a " +
                "LEFT JOIN FETCH a.actor u " +
                "LEFT JOIN FETCH u.roles " +
                "WHERE a.vaultId = :vaultId " +
                "AND a.createdAt >= :startDate " +
                "AND a.createdAt <= :endDate",
        countQuery = "SELECT COUNT(a) FROM AuditLog a " +
                     "WHERE a.vaultId = :vaultId " +
                     "AND a.createdAt >= :startDate " +
                     "AND a.createdAt <= :endDate"
    )
    Page<AuditLog> findVaultActivity(
        @org.springframework.data.repository.query.Param("vaultId") UUID vaultId,
        @org.springframework.data.repository.query.Param("startDate") java.time.Instant startDate,
        @org.springframework.data.repository.query.Param("endDate") java.time.Instant endDate,
        Pageable pageable
    );
}
