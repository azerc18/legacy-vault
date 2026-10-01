package com.ltld.app.legacyvault.entity;

import com.ltld.app.legacyvault.enums.VaultStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Data
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

    // Ràng buộc FK -> users.id (NULL, gán sau)[cite: 9]
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "beneficiary_id")
    private User beneficiary;

    @Column(nullable = false)
    private String name; //[cite: 9]

    @Column(columnDefinition = "TEXT")
    private String description; //[cite: 9]

    // Trạng thái theo ERD: active, unlocked, claimed, archived_locked[cite: 9]
    // Bạn cần tạo thêm Enum VaultStatus trong package enums
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private VaultStatus status = VaultStatus.ACTIVE;

    @Column(name = "unlocked_at")
    private LocalDateTime unlockedAt; //[cite: 9]

    @Column(name = "claim_deadline_at")
    private LocalDateTime claimDeadlineAt; //[cite: 9]

    @Column(name = "archived_at")
    private LocalDateTime archivedAt; //[cite: 9]

    @Column(name = "purge_eligible_at")
    private LocalDateTime purgeEligibleAt; //[cite: 9]

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt; //[cite: 9]

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt; //[cite: 9]
}