package com.ltld.app.legacyvault.entity;

import com.ltld.app.legacyvault.enums.VaultStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
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

    // Ràng buộc FK -> users.id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "beneficiary_id")
    private User beneficiary;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    // Trạng thái theo ERD: active, unlocked, claimed, archived_locked
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private VaultStatus status = VaultStatus.ACTIVE;

    @Column(name = "unlocked_at")
    private LocalDateTime unlockedAt;

    @Column(name = "claim_deadline_at")
    private LocalDateTime claimDeadlineAt;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    @Column(name = "purge_eligible_at")
    private LocalDateTime purgeEligibleAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}