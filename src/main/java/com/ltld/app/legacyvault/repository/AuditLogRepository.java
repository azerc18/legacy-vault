package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.AuditLog;
import com.ltld.app.legacyvault.enums.AuditAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface AuditLogRepository extends Repository<AuditLog, UUID> {
    AuditLog save(AuditLog log);
    boolean existsByVaultIdAndAction(UUID vaultId, AuditAction action);
    Page<AuditLog> findByActor_IdOrderByCreatedAtDesc(UUID actorId, Pageable pageable);

    @org.springframework.data.jpa.repository.Query(
        value = "SELECT a FROM AuditLog a " +
                "LEFT JOIN FETCH a.actor " +
                "WHERE a.vaultId = :vaultId " +
                "AND a.action NOT IN :hiddenActions " +
                "AND a.createdAt >= :startDate " +
                "AND a.createdAt <= :endDate " +
                "ORDER BY a.createdAt DESC, a.id DESC",
        countQuery = "SELECT COUNT(a) FROM AuditLog a " +
                     "WHERE a.vaultId = :vaultId " +
                     "AND a.action NOT IN :hiddenActions " +
                     "AND a.createdAt >= :startDate " +
                     "AND a.createdAt <= :endDate"
    )
    Page<AuditLog> findVaultActivity(
        @org.springframework.data.repository.query.Param("vaultId") UUID vaultId,
        @org.springframework.data.repository.query.Param("hiddenActions") java.util.Collection<AuditAction> hiddenActions,
        @org.springframework.data.repository.query.Param("startDate") java.time.Instant startDate,
        @org.springframework.data.repository.query.Param("endDate") java.time.Instant endDate,
        Pageable pageable
    );

    // FR-05: nhật ký cấp Owner — sự kiện DMS / Executor (vaultId = null) của DmsConfig và mọi ExecutorAssignment
    // (kể cả REVOKED) thuộc Owner. Subquery rỗng => trang rỗng, không sinh "IN ()".
    // FR-05: thêm VAULT_DELETED do chính Owner xóa (có vaultId; Vault đã xóa cứng nên lọc theo actor, không join Vault).
    // FR-05: bỏ các action trong hiddenActions; điều kiện phải có ở CẢ countQuery để totalElements không đếm chúng.
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

    @Query(value = """
    SELECT a FROM AuditLog a LEFT JOIN FETCH a.actor act
    WHERE (:action IS NULL OR a.action = :action)
      AND (:actorEmail IS NULL OR lower(act.email) LIKE :actorEmail)
      AND (:ip IS NULL OR a.ipAddress = :ip)
      AND a.createdAt >= :from AND a.createdAt <= :to
    ORDER BY a.createdAt DESC, a.id DESC
    """,
            countQuery = """
    SELECT COUNT(a) FROM AuditLog a LEFT JOIN a.actor act
    WHERE (:action IS NULL OR a.action = :action)
      AND (:actorEmail IS NULL OR lower(act.email) LIKE :actorEmail)
      AND (:ip IS NULL OR a.ipAddress = :ip)
      AND a.createdAt >= :from AND a.createdAt <= :to
    """)
    Page<AuditLog> searchForAdmin(@Param("action") AuditAction action, @Param("actorEmail") String actorEmail,
                                  @Param("ip") String ip, @Param("from") Instant from, @Param("to") Instant to,
                                  Pageable pageable);

    java.util.List<AuditLog> findByActionInAndCreatedAtAfter(
            java.util.Collection<AuditAction> actions, java.time.Instant after);

    // Cảnh báo bất thường: đếm theo IP trong cửa sổ thời gian
    interface IpCount { String getIp(); Long getTotal(); }

    @org.springframework.data.jpa.repository.Query(
            "SELECT a.ipAddress AS ip, COUNT(a) AS total FROM AuditLog a " +
                    "WHERE a.action = :action AND a.createdAt >= :since AND a.ipAddress IS NOT NULL " +
                    "GROUP BY a.ipAddress HAVING COUNT(a) >= :threshold")
    java.util.List<IpCount> countByIpSince(
            @org.springframework.data.repository.query.Param("action") AuditAction action,
            @org.springframework.data.repository.query.Param("since") java.time.Instant since,
            @org.springframework.data.repository.query.Param("threshold") long threshold);

    // Cảnh báo bất thường: đếm theo người làm trong cửa sổ thời gian
    interface ActorCount { UUID getActorId(); Long getTotal(); }

    @org.springframework.data.jpa.repository.Query(
            "SELECT a.actor.id AS actorId, COUNT(a) AS total FROM AuditLog a " +
                    "WHERE a.action IN :actions AND a.createdAt >= :since AND a.actor IS NOT NULL " +
                    "GROUP BY a.actor.id HAVING COUNT(a) >= :threshold")
    java.util.List<ActorCount> countByActorSince(
            @org.springframework.data.repository.query.Param("actions") java.util.Collection<AuditAction> actions,
            @org.springframework.data.repository.query.Param("since") java.time.Instant since,
            @org.springframework.data.repository.query.Param("threshold") long threshold);
}
