package com.ltld.app.legacyvault.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * V3: cặp khóa ECDSA dùng để ký "biên bản xác nhận" của Legal Verifier.
 * Khóa riêng chỉ lưu ở dạng đã mã hóa bằng CRYPTO_MASTER_KEY; khóa công khai trả ra ngoài để kiểm lại chữ ký.
 */
@Entity
@Table(name = "signing_keys")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SigningKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "key_ref", nullable = false, unique = true, length = 40)
    private String keyRef;

    @Column(nullable = false, length = 40)
    private String algorithm;

    @Column(name = "public_key_pem", nullable = false, columnDefinition = "TEXT")
    private String publicKeyPem;

    @Column(name = "private_key_encrypted", nullable = false, columnDefinition = "TEXT")
    private String privateKeyEncrypted;

    @Column(name = "fingerprint_sha256", nullable = false, length = 64)
    private String fingerprintSha256;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
