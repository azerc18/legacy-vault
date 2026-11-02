package com.ltld.app.legacyvault.entity;

import com.ltld.app.legacyvault.enums.DocumentType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
@Table(name = "legal_documents")
public class LegalDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Nhóm tài liệu (X5): các phiên bản sửa đổi (v1, v2, v3...) của cùng một di chúc/tài liệu
     * có chung document_group_id. Bản có is_current = true là bản có hiệu lực hiện hành.
     */
    @Column(name = "document_group_id", nullable = false)
    private UUID documentGroupId;

    // Giấy tờ này thuộc về Két sắt (Vault) nào
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vault_id")
    private Vault vault;

    // Phân loại tài liệu
    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false)
    private DocumentType documentType;

    // Tên file gốc do người dùng tải lên (vd: dichuc_2026.pdf)
    @Column(name = "file_name", nullable = false)
    private String fileName;

    // ĐÂY LÀ ĐIỂM QUAN TRỌNG: Đường dẫn lưu file phải được mã hóa E2E, hoặc chứa nội dung mã hóa
    @Column(name = "file_url_encrypted", nullable = false, columnDefinition = "TEXT")
    private String fileUrlEncrypted;

    @Column(name = "version", nullable = false)
    @Builder.Default
    private int version = 1;

    @Column(name = "is_current", nullable = false)
    @Builder.Default
    private boolean isCurrent = true;

    @CreationTimestamp
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private LocalDateTime uploadedAt;
}
