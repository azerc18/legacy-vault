package com.ltld.app.legacyvault.entity;

import com.ltld.app.legacyvault.enums.DmsStatus;
import com.ltld.app.legacyvault.enums.NotificationChannel;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "dms_configs", uniqueConstraints = @UniqueConstraint(
        name = "uk_dms_config_owner", columnNames = "owner_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DmsConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false, unique = true)
    private User owner;

    @Column(name = "checkin_interval_days", nullable = false)
    @Builder.Default
    private Integer checkinIntervalDays = 30;

    @Column(name = "reminder_count")
    @Builder.Default
    private Integer reminderCount = 3;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_channel", nullable = false)
    @Builder.Default
    private NotificationChannel notificationChannel = NotificationChannel.EMAIL;

    @Column(name = "backup_activation_wait_days", nullable = false)
    @Builder.Default
    private Integer backupActivationWaitDays = 14;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private DmsStatus status = DmsStatus.ACTIVE;

    @Column(name = "next_checkin_due_at")
    private LocalDateTime nextCheckinDueAt;

    @Column(name = "triggered_at")
    private LocalDateTime triggeredAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
