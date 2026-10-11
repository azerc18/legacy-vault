package com.ltld.app.legacyvault.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

// FR-19: mỗi dòng = Beneficiary (qua claim) đã xem hoặc tải xuống nội dung của một tài sản, ghi nhận lần đầu
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "asset_access_records",
        uniqueConstraints = @UniqueConstraint(name = "uk_access_claim_asset", columnNames = {"claim_id", "asset_id"}))
public class AssetAccessRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "claim_id", nullable = false)
    private BeneficiaryClaim claim;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_id", nullable = false)
    private DigitalAsset asset;

    @Column(name = "first_accessed_at", nullable = false)
    private LocalDateTime firstAccessedAt;
}