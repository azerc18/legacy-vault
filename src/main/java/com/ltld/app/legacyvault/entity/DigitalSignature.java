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
}