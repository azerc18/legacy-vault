package com.ltld.app.legacyvault.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * C.6: bản chụp BẤT BIẾN lúc Vault mở khóa (trường công khai + trạng thái xác thực của từng tài sản).
 * Cố ý không có setter và mọi cột updatable = false; hash đã ghi vào audit log để đối chiếu.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "vault_handover_snapshots",
        uniqueConstraints = @UniqueConstraint(name = "uk_handover_snapshot_vault", columnNames = "vault_id"))
public class VaultHandoverSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JoinColumn(name = "vault_id", nullable = false, updatable = false)
    private Vault vault;

    // mỗi dòng là một JSON phẳng của một tài sản (định dạng JSON Lines)
    @Column(name = "snapshot_data", nullable = false, updatable = false, columnDefinition = "LONGTEXT")
    private String snapshotData;

    @Column(name = "snapshot_hash", nullable = false, updatable = false, length = 64)
    private String snapshotHash;

    @Column(name = "asset_count", nullable = false, updatable = false)
    private int assetCount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
