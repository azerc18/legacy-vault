package com.ltld.app.legacyvault.entity;

import com.ltld.app.legacyvault.enums.DmsStatus;
import com.ltld.app.legacyvault.enums.NotificationChannel;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "dms_configs", uniqueConstraints = @UniqueConstraint(
        name = "uk_dms_config_owner", columnNames = "owner_id"),
        // FR-03: scheduler nhắc nhở tìm các DMS ACTIVE đã tới lần nhắc kế tiếp
        indexes = @Index(name = "idx_dms_config_status_next_reminder", columnList = "status, next_reminder_at"))
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

    /**
     * HẠN CHÓT TRIGGER (= triggerAt của DmsSchedule = lần check-in gần nhất + chu kỳ + thời gian chờ),
     * KHÔNG phải hạn check-in. FR-07 (DmsActivationProcessor.trigger) chuyển DMS sang TRIGGERED khi quá mốc này.
     * Hạn check-in thực sự = lastCheckinAt + checkinIntervalDays.
     */
    @Column(name = "next_checkin_due_at")
    private LocalDateTime nextCheckinDueAt;

    // FR-03: số ngày chờ sau hạn check-in trước khi kích hoạt. Null với dòng tạo trước FR-03.
    @Column(name = "grace_period_days")
    private Integer gracePeriodDays;

    // FR-03: lần Owner check-in (hoặc lưu cấu hình) gần nhất, mốc bắt đầu chu kỳ hiện tại.
    @Column(name = "last_checkin_at")
    private LocalDateTime lastCheckinAt;

    // FR-03: số lần nhắc đã gửi trong chu kỳ hiện tại.
    // DEFAULT 0 để ddl-auto: update thêm cột vào bảng đã có dữ liệu mà không vi phạm NOT NULL.
    @Column(name = "reminders_sent", nullable = false)
    @ColumnDefault("0")
    @Builder.Default
    private int remindersSent = 0;

    // FR-03: thời điểm gửi lần nhắc kế tiếp; null khi đã gửi đủ trong chu kỳ.
    @Column(name = "next_reminder_at")
    private LocalDateTime nextReminderAt;

    @Column(name = "triggered_at")
    private LocalDateTime triggeredAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // B1: khóa lạc quan, chống ghi đè khi scheduler và người dùng sửa cùng lúc.
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
