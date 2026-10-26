package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.VerificationReviewProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface VerificationReviewProgressRepository extends JpaRepository<VerificationReviewProgress, UUID> {

    @Query("SELECT p FROM VerificationReviewProgress p WHERE p.request.id = :requestId AND p.verifierId = :verifierId")
    Optional<VerificationReviewProgress> findByRequestIdAndVerifierId(@Param("requestId") UUID requestId,
                                                                      @Param("verifierId") UUID verifierId);

    // Hồ sơ được mở lại về PENDING: xóa tiến độ của mọi verifier
    @Modifying
    @Query("DELETE FROM VerificationReviewProgress p WHERE p.request.id = :requestId")
    int deleteByRequestId(@Param("requestId") UUID requestId);
}
