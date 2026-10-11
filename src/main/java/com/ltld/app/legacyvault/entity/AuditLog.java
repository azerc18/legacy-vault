package com.ltld.app.legacyvault.entity;

import com.ltld.app.legacyvault.enums.AuditAction;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.Immutable;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Immutable   // Hibernate sẽ không bao giờ phát sinh UPDATE
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_actor",   columnList = "actor_id"),
        @Index(name = "idx_audit_action",  columnList = "action"),
        @Index(name = "idx_audit_vault",   columnList = "vault_id"),
        @Index(name = "idx_audit_entity",  columnList = "entity_type, entity_id"),
        @Index(name = "idx_audit_created", columnList = "created_at")})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // NULL = hệ thống tự động thực hiện
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 100)
    private AuditAction action;

    @Column(name = "entity_type", length = 100)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(name = "vault_id")
    private UUID vaultId;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private Map<String, Object> metadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
