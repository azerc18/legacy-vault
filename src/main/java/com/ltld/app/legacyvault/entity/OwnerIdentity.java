package com.ltld.app.legacyvault.entity;

import com.ltld.app.legacyvault.enums.KycSource;
import com.ltld.app.legacyvault.enums.VnProvince;
import com.ltld.app.legacyvault.utility.CitizenId;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Thông tin CCCD Owner đã xác minh (KYC BASIC). Số CCCD / CMND chỉ lưu bản mã hoá và HMAC để tra trùng. */
@Entity
@Table(name = "owner_identities")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OwnerIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "citizen_id_encrypted", nullable = false, length = 512)
    private String citizenIdEncrypted;

    @Column(name = "citizen_id_hash", nullable = false, unique = true, length = 64)
    private String citizenIdHash;

    @Column(name = "citizen_id_masked", nullable = false, length = 20)
    private String citizenIdMasked;

    @Column(name = "old_id_encrypted", length = 512)
    private String oldIdEncrypted;

    @Column(name = "old_id_hash", length = 64)
    private String oldIdHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private CitizenId.Gender gender;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "residence_province", nullable = false, length = 30)
    private VnProvince residenceProvince;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private KycSource source;

    @Column(name = "ai_ocr_consent", nullable = false)
    private boolean aiOcrConsent;

    @Column(name = "consent_at", nullable = false)
    private Instant consentAt;

    @Column(name = "verified_at", nullable = false)
    private Instant verifiedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
