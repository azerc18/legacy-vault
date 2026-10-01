package com.ltld.app.legacyvault.entity;

import com.ltld.app.legacyvault.enums.VaultStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "vaults")
public class Vault {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Ràng buộc FK -> users.id (NOT NULL)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "beneficiary_id")
    private User beneficiary;

    @Column(nullable = false)

    @Column(columnDefinition = "TEXT")

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private VaultStatus status = VaultStatus.ACTIVE;

    @Column(name = "unlocked_at")

    @Column(name = "claim_deadline_at")

    @Column(name = "archived_at")

    @Column(name = "purge_eligible_at")

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)

    @UpdateTimestamp
    @Column(name = "updated_at")
}