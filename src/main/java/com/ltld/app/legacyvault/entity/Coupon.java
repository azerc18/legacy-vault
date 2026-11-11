package com.ltld.app.legacyvault.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/** Việc 4: mã giảm giá theo %, dùng khi thanh toán gói VIP. Mã không đổi sau khi tạo; chỉ bật / tắt. */
@Entity
@Table(name = "coupons")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(name = "percent_off", nullable = false)
    private int percentOff;

    /** null = dùng cho mọi gói VIP. */
    @Column(name = "plan_code", length = 50)
    private String planCode;

    /** null = không giới hạn số lượt. */
    @Column(name = "max_redemptions")
    private Integer maxRedemptions;

    @Column(name = "redeemed_count", nullable = false)
    @Builder.Default
    private int redeemedCount = 0;

    @Column(name = "valid_until")
    private LocalDateTime validUntil;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(length = 255)
    private String note;

    @Column(name = "created_by")
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
