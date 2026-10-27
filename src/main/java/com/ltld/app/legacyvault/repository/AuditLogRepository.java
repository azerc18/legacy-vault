package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.AuditLog;
import com.ltld.app.legacyvault.enums.AuditAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

import java.util.UUID;

public interface AuditLogRepository extends Repository<AuditLog, UUID> {
    AuditLog save(AuditLog log);
    boolean existsByVaultIdAndAction(UUID vaultId, AuditAction action);
    Page<AuditLog> findByActor_IdOrderByCreatedAtDesc(UUID actorId, Pageable pageable);

    @org.springframework.data.jpa.repository.Query(
        value = "SELECT a FROM AuditLog a " +
                "LEFT JOIN FETCH a.actor " +
                "WHERE a.vaultId = :vaultId " +
                "AND a.createdAt >= :startDate " +
                "AND a.createdAt <= :endDate " +
                "ORDER BY a.createdAt DESC, a.id DESC",
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

    // FR-05: nhật ký cấp Owner — sự kiện DMS / Executor (vaultId = null) của DmsConfig và mọi ExecutorAssignment
    // (kể cả REVOKED) thuộc Owner. Subquery rỗng => trang rỗng, không sinh "IN ()".
    // Y3: thêm VAULT_DELETED do chính Owner xóa (có vaultId; Vault đã xóa cứng nên lọc theo actor, không join Vault).
    // Y2: bỏ các action trong hiddenActions; điều kiện phải có ở CẢ countQuery để totalElements không đếm chúng.
    @org.springframework.data.jpa.repository.Query(
        value = "SELECT a FROM AuditLog a " +
                "LEFT JOIN FETCH a.actor " +
                "WHERE ((a.vaultId IS NULL " +
                "AND ((a.entityType = :dmsConfigType " +
                "AND a.entityId IN (SELECT c.id FROM DmsConfig c WHERE c.owner.id = :ownerId)) " +
                "OR (a.entityType = :assignmentType " +
                "AND a.entityId IN (SELECT e.id FROM ExecutorAssignment e WHERE e.owner.id = :ownerId)))) " +
                "OR (a.action = com.ltld.app.legacyvault.enums.AuditAction.VAULT_DELETED " +
                "AND a.actor.id = :ownerId)) " +
                "AND a.action NOT IN :hiddenActions " +
                "AND a.createdAt >= :startDate " +
                "AND a.createdAt <= :endDate " +
                "ORDER BY a.createdAt DESC, a.id DESC",
        countQuery = "SELECT COUNT(a) FROM AuditLog a " +
                     "WHERE ((a.vaultId IS NULL " +
                     "AND ((a.entityType = :dmsConfigType " +
                     "AND a.entityId IN (SELECT c.id FROM DmsConfig c WHERE c.owner.id = :ownerId)) " +
                     "OR (a.entityType = :assignmentType " +
                     "AND a.entityId IN (SELECT e.id FROM ExecutorAssignment e WHERE e.owner.id = :ownerId)))) " +
                     "OR (a.action = com.ltld.app.legacyvault.enums.AuditAction.VAULT_DELETED " +
                     "AND a.actor.id = :ownerId)) " +
                     "AND a.action NOT IN :hiddenActions " +
                     "AND a.createdAt >= :startDate " +
                     "AND a.createdAt <= :endDate"
    )
    Page<AuditLog> findOwnerActivity(
        @org.springframework.data.repository.query.Param("ownerId") UUID ownerId,
        @org.springframework.data.repository.query.Param("dmsConfigType") String dmsConfigType,
        @org.springframework.data.repository.query.Param("assignmentType") String assignmentType,
        @org.springframework.data.repository.query.Param("hiddenActions") java.util.Collection<AuditAction> hiddenActions,
        @org.springframework.data.repository.query.Param("startDate") java.time.Instant startDate,
        @org.springframework.data.repository.query.Param("endDate") java.time.Instant endDate,
        Pageable pageable
    );
}
