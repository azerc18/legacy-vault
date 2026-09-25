package com.ltld.app.legacyvault.beneficiary.entity;

import com.ltld.app.legacyvault.beneficiary.enums.VerificationMethod;
import com.ltld.app.legacyvault.beneficiary.enums.VerificationStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "identity_verifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class IdentityVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "beneficiary_id", nullable = false)
    private UUID beneficiaryId;

    @Column(name = "vault_id", nullable = false)
    private UUID vaultId;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false)
    private VerificationMethod method;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private VerificationStatus status = VerificationStatus.PENDING;

    @Column(name = "attempt_count")
    @Builder.Default
    private Integer attemptCount = 0;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
