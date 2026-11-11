package com.ltld.app.legacyvault.entity;

import com.ltld.app.legacyvault.enums.SubscriptionStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

// T1: Gói thuê bao của từng người dùng
@Entity
@Table(name = "subscriptions", indexes = {
        @Index(name = "idx_subscription_user_status", columnList = "user_id, status"),
        @Index(name = "idx_subscription_status_expires", columnList = "status, expires_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "plan_id", nullable = false)
    private SubscriptionPlan plan;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "reminder_sent", nullable = false)
    @Builder.Default
    private boolean reminderSent = false;

    // Việc 6: Owner đã hủy gia hạn; vẫn giữ VIP tới expiresAt rồi về FREE, không hoàn tiền
    @Column(name = "cancel_at_period_end", nullable = false)
    @Builder.Default
    private boolean cancelAtPeriodEnd = false;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    // Việc 4: gói dùng thử miễn phí (mỗi tài khoản một lần)
    @Column(nullable = false)
    @Builder.Default
    private boolean trial = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

