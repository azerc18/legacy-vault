package com.ltld.app.legacyvault.entity;

import com.ltld.app.legacyvault.enums.ExecutorResponseStatus;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.ExecutorType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "executor_assignments",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_executor_assignment_owner_type",
                columnNames = {"owner_id", "executor_type"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExecutorAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "executor_user_id", nullable = false)
    private User executorUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "executor_type", nullable = false)
    private ExecutorType executorType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ExecutorStatus status = ExecutorStatus.INACTIVE;

    // Lời mời làm Executor: Owner chỉ định -> PENDING; Executor đồng ý -> ACCEPTED; từ chối -> DECLINED (+ REVOKED).
    // Mặc định ACCEPTED cho dữ liệu cũ và cho Admin chỉ định khi cứu hộ; Owner chỉ định luôn đặt PENDING.
    @Enumerated(EnumType.STRING)
    @Column(name = "response_status", nullable = false)
    @Builder.Default
    private ExecutorResponseStatus responseStatus = ExecutorResponseStatus.ACCEPTED;

    // Lúc Executor đồng ý / từ chối lời mời hiện tại (null khi đang chờ hoặc dữ liệu cũ)
    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    // Lý do từ chối (không bắt buộc), Owner xem trên trang Người thi hành
    @Column(name = "decline_reason", length = 500)
    private String declineReason;

    // Hạn trả lời lời mời (ExecutorInvitationPolicy, mặc định 7 ngày kể từ lúc chỉ định).
    // null = dữ liệu cũ hoặc dòng không phải lời mời đang chờ -> không bao giờ hết hạn.
    @Column(name = "invitation_expires_at")
    private LocalDateTime invitationExpiresAt;

    // Lúc đã gửi mail nhắc sắp hết hạn; giữ để không nhắc lại mỗi lần job quét
    @Column(name = "invitation_reminder_sent_at")
    private LocalDateTime invitationReminderSentAt;

    // FR-06: thời điểm Owner chỉ định người hiện tại (dòng có thể được dùng lại khi thay người,
    // nên createdAt không phản ánh lần chỉ định gần nhất). NULL với dữ liệu cũ trước FR-06.
    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    // FR-07: thời điểm Executor đăng nhập và xác nhận đã nhận thông báo kích hoạt
    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // B1: khóa lạc quan, chống ghi đè khi scheduler và người dùng sửa cùng lúc.
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
