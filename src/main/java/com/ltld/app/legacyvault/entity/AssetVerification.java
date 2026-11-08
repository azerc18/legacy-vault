package com.ltld.app.legacyvault.entity;

import com.ltld.app.legacyvault.enums.AssetVerificationMethod;
import com.ltld.app.legacyvault.enums.AssetVerificationStatus;
import com.ltld.app.legacyvault.enums.VerificationProvider;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

// C.5: một lần xác thực của một tài sản (lấy dòng mới nhất làm trạng thái hiện tại)
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "asset_verifications",
        indexes = @Index(name = "idx_asset_verification_asset", columnList = "asset_id"))
public class AssetVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private DigitalAsset asset;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AssetVerificationMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private VerificationProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssetVerificationStatus status;

    // hash của các trường công khai quan trọng lúc xác thực; đổi trường => về SELF_DECLARED
    @Column(length = 64)
    private String fingerprint;

    @Column(name = "challenge_hash", length = 64)
    private String challengeHash;

    // bằng chứng KHÔNG nhạy cảm (nhạy cảm thì lưu qua kho tài liệu mã hóa)
    @Column(name = "evidence_json", columnDefinition = "TEXT")
    private String evidenceJson;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
