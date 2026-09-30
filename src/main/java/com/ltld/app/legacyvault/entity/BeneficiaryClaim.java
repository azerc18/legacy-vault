package com.ltld.app.legacyvault.entity;

import com.ltld.app.legacyvault.enums.ClaimStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "beneficiary_claims")
public class BeneficiaryClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Thiết lập FK trỏ tới bảng vaults (1 Vault chỉ có 1 Claim)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vault_id", nullable = false, unique = true)
    private Vault vault;

    // Thiết lập FK trỏ tới bảng users (1 User có thể là Beneficiary của nhiều Claim)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "beneficiary_id", nullable = false)
    private User beneficiary;

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