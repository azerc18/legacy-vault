package com.ltld.app.legacyvault.entity;

import com.ltld.app.legacyvault.enums.VerificationMethod;
import com.ltld.app.legacyvault.enums.VerificationStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@DynamicUpdate
@Table(name = "identity_verifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdentityVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Đổi tên biến thành beneficiary cho rõ nghĩa và đồng bộ
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "beneficiary_id", nullable = false)
    private User beneficiary;

    // Thiết lập FK trỏ tới bảng vaults
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vault_id", nullable = false)
    private Vault vault;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false)
    private VerificationMethod method;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private VerificationStatus status = VerificationStatus.PENDING;

    @Column(name = "attempt_count")
    @Builder.Default
    private Integer attemptCount = 0;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    // FR-17: chỉ lưu HASH (SHA-256 hex = 64 ký tự) của OTP, không lưu OTP gốc.
    // NULL khi chưa gửi OTP hoặc sau khi đã dùng xong.
    @Column(name = "otp_code", length = 64)
    private String otpCode;

    // FR-17: thời điểm sinh OTP gần nhất, dùng để chặn gửi lại quá nhanh
    @Column(name = "otp_sent_at")
    private LocalDateTime otpSentAt;

    // FR-17: thời điểm OTP hết hạn
    @Column(name = "otp_expires_at")
    private LocalDateTime otpExpiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}