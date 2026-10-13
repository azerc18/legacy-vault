package com.ltld.app.legacyvault.entity;

import com.ltld.app.legacyvault.enums.NotificationChannel;
import com.ltld.app.legacyvault.enums.NotificationStatus;
import com.ltld.app.legacyvault.enums.NotificationType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private NotificationChannel channel = NotificationChannel.EMAIL;

    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private NotificationStatus status = NotificationStatus.QUEUED;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    // B2: số lần đã thử gửi và lần thử gần nhất, để giới hạn gửi lại thông báo FAILED.
    // DEFAULT 0 để ddl-auto: update thêm cột vào bảng đã có dữ liệu mà không vi phạm NOT NULL.
    @Column(name = "attempt_count", nullable = false)
    @ColumnDefault("0")
    @Builder.Default
    private Integer attemptCount = 0;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;
}
