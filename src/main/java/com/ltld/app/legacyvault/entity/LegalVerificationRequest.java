package com.ltld.app.legacyvault.entity;

import com.ltld.app.legacyvault.enums.LegalVerificationStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "legal_verification_requests")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class LegalVerificationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "executor_id", nullable = false)
    private User executor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verifier_id")
    private User verifier;

    @Column(name = "death_certificate_file_url_encrypted", nullable = false, length = 500)
    private String deathCertificateFileUrlEncrypted;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private LegalVerificationStatus status = LegalVerificationStatus.PENDING;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}