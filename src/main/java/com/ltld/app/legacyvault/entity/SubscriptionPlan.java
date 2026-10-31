package com.ltld.app.legacyvault.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

// T1: Danh mục gói thuê bao (FREE, VIP_MONTH, VIP_YEAR)
@Entity
@Table(name = "subscription_plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionPlan {

    public static final String CODE_FREE = "FREE";
    public static final String CODE_VIP_MONTH = "VIP_MONTH";
    public static final String CODE_VIP_YEAR = "VIP_YEAR";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "price_vnd", nullable = false)
    private long priceVnd;

    // NULL với gói FREE (không hết hạn)
    @Column(name = "duration_days")
    private Integer durationDays;

    @Column(name = "max_vaults", nullable = false)
    @Builder.Default
    private int maxVaults = 1;

    @Column(name = "max_beneficiaries", nullable = false)
    @Builder.Default
    private int maxBeneficiaries = 1;

    @Column(name = "storage_quota_bytes", nullable = false)
    @Builder.Default
    private long storageQuotaBytes = 50L * 1024 * 1024;

    @Column(name = "legacy_messages_enabled", nullable = false)
    @Builder.Default
    private boolean legacyMessagesEnabled = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public boolean isVip() {
        return !CODE_FREE.equalsIgnoreCase(code);
    }
}

