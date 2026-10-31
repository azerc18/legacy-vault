package com.ltld.app.legacyvault.entity;

import com.ltld.app.legacyvault.enums.BeneficiaryInviteStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

// N2a: mỗi dòng là một người thụ hưởng của một vault
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "vault_beneficiaries",
        uniqueConstraints = @UniqueConstraint(name = "uk_vault_beneficiary_email",
                columnNames = {"vault_id", "email"}))
public class VaultBeneficiary {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vault_id", nullable = false)
    private Vault vault;

    // NULL khi người này chưa có tài khoản đã xác minh
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private BeneficiaryInviteStatus status = BeneficiaryInviteStatus.PENDING_REGISTRATION;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}