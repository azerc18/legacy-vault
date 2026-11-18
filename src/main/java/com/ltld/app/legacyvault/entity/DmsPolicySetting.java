package com.ltld.app.legacyvault.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Chính sách Dead Man's Switch do Admin chỉnh ở trang "Chính sách hệ thống". Chỉ có 1 dòng (id = {@link #SINGLETON_ID}).
 * Giá trị mặc định khớp với các số trước đây viết cứng trong code, nên chưa ai sửa thì hành vi không đổi.
 */
@Entity
@Table(name = "dms_policy_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DmsPolicySetting {

    public static final byte SINGLETON_ID = 1;

    @Id
    private Byte id;

    @Column(name = "checkin_interval_min_days", nullable = false)
    @Builder.Default
    private int checkinIntervalMinDays = 1;

    @Column(name = "checkin_interval_default_days", nullable = false)
    @Builder.Default
    private int checkinIntervalDefaultDays = 30;

    @Column(name = "checkin_interval_max_days", nullable = false)
    @Builder.Default
    private int checkinIntervalMaxDays = 365;

    @Column(name = "grace_period_min_days", nullable = false)
    @Builder.Default
    private int gracePeriodMinDays = 1;

    @Column(name = "grace_period_default_days", nullable = false)
    @Builder.Default
    private int gracePeriodDefaultDays = 7;

    @Column(name = "grace_period_max_days", nullable = false)
    @Builder.Default
    private int gracePeriodMaxDays = 90;

    @Column(name = "reminder_count_min", nullable = false)
    @Builder.Default
    private int reminderCountMin = 1;

    @Column(name = "reminder_count_default", nullable = false)
    @Builder.Default
    private int reminderCountDefault = 3;

    @Column(name = "reminder_count_max", nullable = false)
    @Builder.Default
    private int reminderCountMax = 10;

    @Column(name = "backup_activation_wait_days", nullable = false)
    @Builder.Default
    private int backupActivationWaitDays = 14;

    /** Số giờ chờ Trusted Confirmer trả lời trước khi DMS tự kích hoạt (trước đây viết cứng 72). */
    @Column(name = "confirmer_wait_hours", nullable = false)
    @Builder.Default
    private int confirmerWaitHours = 72;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
