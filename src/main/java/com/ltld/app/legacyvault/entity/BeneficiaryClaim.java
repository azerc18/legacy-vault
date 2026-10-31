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
@Table(name = "beneficiary_claims",
        uniqueConstraints = @UniqueConstraint(name = "uk_claim_vault_beneficiary",
                columnNames = {"vault_id", "beneficiary_id"}))
public class BeneficiaryClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // N2b: 1 Vault có nhiều Claim (mỗi người thụ hưởng một claim)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vault_id", nullable = false)
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

    // FR-16: số lần đã thử gửi thông báo bàn giao (tối đa MAX_NOTIFY_ATTEMPTS ở BeneficiaryNotificationListener)
    @Builder.Default
    @Column(name = "notify_attempts", nullable = false, columnDefinition = "int default 0")
    private int notifyAttempts = 0;

    // FR-16 (R1): thời điểm thử gửi gần nhất, dùng để giãn cách các lần thử lại
    @Column(name = "last_notify_attempt_at")
    private LocalDateTime lastNotifyAttemptAt;
}