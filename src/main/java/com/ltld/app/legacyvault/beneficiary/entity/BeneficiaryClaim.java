package com.ltld.app.legacyvault.beneficiary.entity;

import com.ltld.app.legacyvault.beneficiary.enums.ClaimStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "beneficiary_claims")

public class BeneficiaryClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "vault_id", nullable = false, unique = true)
    private UUID vaultId;

    @Column(name = "beneficiary_id", nullable = false)
    private UUID beneficiaryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ClaimStatus status;

    @Column(name = "notified_at")
    private LocalDateTime notifiedAt;

    @Column(name = "claim_deadline_at", nullable = false)
    private LocalDateTime claimDeadlineAt;

    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;
}
