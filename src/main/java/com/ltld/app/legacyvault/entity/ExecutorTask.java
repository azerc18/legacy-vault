package com.ltld.app.legacyvault.entity;

import com.ltld.app.legacyvault.enums.ExecutorTaskStatus;
import com.ltld.app.legacyvault.enums.ExecutorTaskWish;
import com.ltld.app.legacyvault.enums.TaskConfirmationStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

// F5: một việc Executor cần làm với tài khoản số (MXH / email / ví điện tử) sau khi Vault mở khóa
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "executor_tasks",
        uniqueConstraints = @UniqueConstraint(name = "uk_executor_task_asset", columnNames = "asset_id"),
        indexes = @Index(name = "idx_executor_task_vault", columnList = "vault_id"))
public class ExecutorTask {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vault_id", nullable = false)
    private Vault vault;

    // Mỗi tài sản chỉ sinh một task (unique), nên sinh lại nhiều lần cũng không trùng
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private DigitalAsset asset;

    // Chép từ trường công khai của tài sản lúc sinh task (Vault đã khóa sau khi mở, tài sản không đổi nữa)
    @Column(length = 100)
    private String platform;

    @Column(name = "account_handle")
    private String accountHandle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExecutorTaskWish wish;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ExecutorTaskStatus status = ExecutorTaskStatus.TODO;

    // Ghi chú của Executor (ví dụ mã yêu cầu đã gửi nhà cung cấp), Beneficiary xem được
    @Column(length = 1000)
    private String note;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    // C.4: bằng chứng hoàn tất. Bắt buộc có một trong hai để chuyển DONE (kiểm trong ExecutorTaskServiceImpl.update)
    @Column(name = "evidence_ref", length = 200)
    private String evidenceRef;

    @Column(name = "evidence_document_id")
    private UUID evidenceDocumentId;

    // PR 2 (C.4): thông tin tệp bằng chứng, chép từ executor_evidence_files lúc tải lên
    @Column(name = "evidence_file_name")
    private String evidenceFileName;

    @Column(name = "evidence_sha256", length = 64)
    private String evidenceSha256;

    @Column(name = "evidence_uploaded_at")
    private LocalDateTime evidenceUploadedAt;

    // PR 2 (C.6): xác nhận chéo. null = chưa DONE hoặc DONE từ trước PR 2
    @Enumerated(EnumType.STRING)
    @Column(name = "confirmation_status", length = 20)
    private TaskConfirmationStatus confirmationStatus;

    @Column(name = "confirm_deadline_at")
    private LocalDateTime confirmDeadlineAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "confirmed_by")
    private UUID confirmedBy;

    @Column(name = "dispute_reason", length = 500)
    private String disputeReason;

    @Column(name = "disputed_at")
    private LocalDateTime disputedAt;

    @Column(name = "disputed_by")
    private UUID disputedBy;
}
