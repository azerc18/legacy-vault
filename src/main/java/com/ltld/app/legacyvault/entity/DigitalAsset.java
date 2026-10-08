package com.ltld.app.legacyvault.entity;

import com.ltld.app.legacyvault.enums.AssetStatus;
import com.ltld.app.legacyvault.enums.AssetType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
@Table(name = "digital_assets")
public class DigitalAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Liên kết nhiều-một (Many-to-One): Nhiều tài sản số sẽ thuộc về 1 Vault
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vault_id", nullable = false)
    private Vault vault;

    // Phân loại tài sản (nhúng Enum AssetType vừa tạo)
    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false)
    private AssetType assetType;

    @Column(name = "asset_name", nullable = false)
    private String assetName;

    // CHÚ Ý: Đây là nơi lưu mật khẩu/key. Bắt buộc phải là dạng đã MÃ HÓA (encrypted)
    @Column(name = "encrypted_secret", nullable = false, columnDefinition = "TEXT")
    private String encryptedSecret;

    // Tham chiếu đến khóa dùng để mã hóa
    @Column(name = "encryption_key_ref", nullable = false)
    private String encryptionKeyRef;

    // Ghi chú của tài sản cũng nên được mã hóa để bảo mật
    @Column(name = "notes_encrypted", columnDefinition = "TEXT")
    private String notesEncrypted;

    @Column(name = "attachment_url", length = 500)
    private String attachmentUrl;

    // Trạng thái mặc định khi tạo mới là ACTIVE
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AssetStatus status = AssetStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
