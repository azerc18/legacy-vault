package com.ltld.app.legacyvault.entity;

import com.ltld.app.legacyvault.enums.AppLanguage;
import com.ltld.app.legacyvault.enums.KycLevel;
import com.ltld.app.legacyvault.enums.UserStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "full_name",nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private UserStatus status = UserStatus.PENDING;

    @Column(name = "kyc_level")
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private KycLevel kycLevel = KycLevel.NONE;

    @Builder.Default
    @Column(name = "failed_login_attempts")
    private int failedLoginAttempts = 0;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at")
    @CreationTimestamp
    private Instant createdAt;

    @Column(name = "updated_at")
    @UpdateTimestamp
    private Instant updatedAt;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_language", nullable = false, length = 5)
    private AppLanguage preferredLanguage = AppLanguage.DEFAULT;

    @Column(name = "avatar_path")
    private String avatarPath;

    @Column(name = "avatar_updated_at")
    private Instant avatarUpdatedAt;

    // Thời điểm người dùng tự xoá tài khoản (status = DELETED)
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Builder.Default
    @Column(name = "mfa_enabled", nullable = false)
    private boolean mfaEnabled = false;

    @Column(name = "mfa_secret", length = 512)
    private String mfaSecret;                 // đã mã hoá

    @Column(name = "mfa_last_used_step")
    private Long mfaLastUsedStep;

    @Column(name = "mfa_challenge_hash", length = 64)
    private String mfaChallengeHash;

    @Column(name = "mfa_challenge_expires_at")
    private Instant mfaChallengeExpiresAt;

    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    Set<Role> roles = new HashSet<>();
}
