package com.ltld.app.legacyvault.entity;

import com.ltld.app.legacyvault.enums.SecurityAlertRule;
import com.ltld.app.legacyvault.enums.SecurityAlertSeverity;
import com.ltld.app.legacyvault.enums.SecurityAlertStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "security_alerts", indexes = {
        @Index(name = "idx_alert_status_created", columnList = "status, created_at"),
        @Index(name = "idx_alert_rule_subject", columnList = "rule, subject")})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private SecurityAlertRule rule;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SecurityAlertSeverity severity;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SecurityAlertStatus status = SecurityAlertStatus.OPEN;

    // Khoá chống trùng: IP, userId hoặc id audit log tuỳ luật
    @Column(nullable = false, length = 100)
    private String subject;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "event_count", nullable = false)
    private int eventCount;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "acknowledged_by")
    private UUID acknowledgedBy;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private Long version;
}