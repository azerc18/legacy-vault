package com.ltld.app.legacyvault.entity;

import com.ltld.app.legacyvault.enums.AllocationMode;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

// N2a: quan hệ N:M giữa tài sản và người thụ hưởng (ai được nhận tài sản nào)
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "asset_allocations",
        uniqueConstraints = @UniqueConstraint(name = "uk_allocation_asset_beneficiary",
                columnNames = {"asset_id", "vault_beneficiary_id"}))
public class AssetAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false)
    private DigitalAsset asset;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vault_beneficiary_id", nullable = false)
    private VaultBeneficiary beneficiary;

    // Người nhận dự phòng khi người chính Decline hoặc quá hạn
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contingent_beneficiary_id")
    private VaultBeneficiary contingentBeneficiary;

    // Người đang giữ quyền nhận (NULL = người chính); được gán khi fallback
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "effective_beneficiary_id")
    private VaultBeneficiary effectiveBeneficiary;

    @Enumerated(EnumType.STRING)
    @Column(name = "allocation_mode", nullable = false)
    @Builder.Default
    private AllocationMode mode = AllocationMode.EXCLUSIVE;

    @Column(name = "share_percent", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal sharePercent = new BigDecimal("100.00");

    @Column(name = "allocation_note", columnDefinition = "TEXT")
    private String note;
}
