package com.ltld.app.legacyvault.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * FR-13: tiến độ thẩm định của TỪNG Legal Verifier trên một hồ sơ (Check → View).
 * Mỗi cặp (hồ sơ, verifier) một dòng nên nhiều verifier cùng xem hồ sơ PENDING không xóa tiến độ của nhau.
 */
@Entity
@Table(name = "verification_review_progress",
        uniqueConstraints = @UniqueConstraint(columnNames = {"request_id", "verifier_id"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class VerificationReviewProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private LegalVerificationRequest request;

    @Column(name = "verifier_id", nullable = false)
    private UUID verifierId;

    @Column(name = "viewed_at")
    private LocalDateTime viewedAt;

    @Column(name = "checked_at")
    private LocalDateTime checkedAt;

    @Column(name = "document_valid")
    private Boolean documentValid;
}
