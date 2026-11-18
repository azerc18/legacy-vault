package com.ltld.app.legacyvault.entity;

import com.ltld.app.legacyvault.enums.SignatureMethod;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "digital_signatures")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class DigitalSignature {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false, unique = true)
    private LegalVerificationRequest request;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "verifier_id", nullable = false)
    private User verifier;

    @Enumerated(EnumType.STRING)
    @Column(name = "signature_method", nullable = false)
    private SignatureMethod signatureMethod;

    @Column(name = "signature_hash", nullable = false)
    private String signatureHash;

    @CreationTimestamp
    @Column(name = "signed_at", nullable = false, updatable = false)
    private LocalDateTime signedAt;

    // V3: "con dấu" kiểm lại được. NULL ở các chữ ký tạo trước khi có tính năng này.
    // SHA-256 (hex) của giấy chứng tử đã giải mã tại thời điểm ký
    @Column(name = "document_sha256", length = 64)
    private String documentSha256;

    // Chuỗi chuẩn hóa đã được ký (xem VerifierSignatureSealService), lưu nguyên văn để kiểm lại từng byte
    @Column(name = "signed_payload", columnDefinition = "TEXT")
    private String signedPayload;

    // Chữ ký ECDSA dạng DER, mã hóa Base64
    @Column(name = "signature_value", columnDefinition = "TEXT")
    private String signatureValue;

    @Column(name = "signature_algorithm", length = 40)
    private String signatureAlgorithm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "signing_key_id")
    private SigningKey signingKey;

    // Chữ ký tay (method = HANDWRITTEN): ảnh PNG Base64 đã mã hóa bằng CRYPTO_MASTER_KEY; null với OTP
    @Column(name = "signature_image_encrypted", columnDefinition = "MEDIUMTEXT")
    private String signatureImageEncrypted;

    // SHA-256 (hex) của ảnh PNG gốc; nằm trong payload đã ký
    @Column(name = "signature_image_sha256", length = 64)
    private String signatureImageSha256;
}
