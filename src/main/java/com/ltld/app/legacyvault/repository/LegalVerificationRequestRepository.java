package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.LegalVerificationRequest;
import com.ltld.app.legacyvault.enums.LegalVerificationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LegalVerificationRequestRepository extends JpaRepository<LegalVerificationRequest, UUID> {

    // Lấy luôn owner + executor để tránh N+1 khi map sang DTO
    @Query("SELECT r FROM LegalVerificationRequest r JOIN FETCH r.owner JOIN FETCH r.executor WHERE r.status = :status")
    List<LegalVerificationRequest> findByStatusFetch(@Param("status") LegalVerificationStatus status);

    // Q23-b + N6: Loại hồ sơ mà verifier gọi là Owner hoặc Executor
    @Query("""
        SELECT r FROM LegalVerificationRequest r JOIN FETCH r.owner o JOIN FETCH r.executor e
        WHERE r.status = :status AND o.id <> :verifierId AND e.id <> :verifierId
    """)
    List<LegalVerificationRequest> findPendingVisibleTo(@Param("status") LegalVerificationStatus status,
                                                        @Param("verifierId") UUID verifierId);

    List<LegalVerificationRequest> findByVerifierIdOrderByDecidedAtDesc(UUID verifierId);

    // Khóa bi quan khi ký: tránh 2 request ký song song → lỗi 500
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM LegalVerificationRequest r WHERE r.id = :id")
    Optional<LegalVerificationRequest> findByIdForUpdate(@Param("id") UUID id);
}