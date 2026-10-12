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

    @CreationTimestamp
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private LocalDateTime uploadedAt;
}
