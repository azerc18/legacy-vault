package com.ltld.app.legacyvault.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

// C.4: tệp bằng chứng Executor tải lên (nội dung đã mã hóa, đường dẫn trỏ tới kho file). Mỗi task một tệp.
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "executor_evidence_files",
        uniqueConstraints = @UniqueConstraint(name = "uk_evidence_task", columnNames = "task_id"),
        indexes = @Index(name = "idx_evidence_vault", columnList = "vault_id"))
public class ExecutorEvidenceFile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private ExecutorTask task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vault_id", nullable = false)
    private Vault vault;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_url_encrypted", nullable = false, columnDefinition = "TEXT")
    private String fileUrlEncrypted;

    // kích thước SAU mã hóa
    @Column(name = "size_bytes")
    private Long sizeBytes;

    // hash của nội dung GỐC (trước mã hóa)
    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;
}
