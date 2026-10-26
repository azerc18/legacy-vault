package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.LegalVerificationRequest;
import com.ltld.app.legacyvault.enums.LegalVerificationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LegalVerificationRequestRepository extends JpaRepository<LegalVerificationRequest, UUID> {

    // Q23-b + N6: Loại hồ sơ mà verifier gọi là Owner hoặc Executor
    @Query("""
        SELECT r FROM LegalVerificationRequest r JOIN FETCH r.owner o JOIN FETCH r.executor e
        WHERE r.status = :status AND o.id <> :verifierId AND e.id <> :verifierId
        ORDER BY r.createdAt ASC
    """)
    List<LegalVerificationRequest> findPendingVisibleTo(@Param("status") LegalVerificationStatus status,
                                                        @Param("verifierId") UUID verifierId);

    // Đếm hồ sơ chờ duyệt (cùng điều kiện loại xung đột lợi ích với findPendingVisibleTo)
    @Query("""
        SELECT COUNT(r) FROM LegalVerificationRequest r
        WHERE r.status = :status AND r.owner.id <> :verifierId AND r.executor.id <> :verifierId
    """)
    long countPendingVisibleTo(@Param("status") LegalVerificationStatus status,
                               @Param("verifierId") UUID verifierId);

    // Đếm hồ sơ verifier đã xử lý theo trạng thái
    long countByVerifierIdAndStatus(UUID verifierId, LegalVerificationStatus status);

    // M2: lịch sử lọc + phân trang ngay trong DB, lấy luôn owner + executor (tránh lazy N+1)
    @Query("""
        SELECT r FROM LegalVerificationRequest r JOIN FETCH r.owner JOIN FETCH r.executor
        WHERE r.verifier.id = :verifierId
          AND r.status IN :statuses
          AND r.decidedAt >= :fromTime AND r.decidedAt < :toTime
                ORDER BY r.decidedAt DESC, r.id DESC
    """)
    List<LegalVerificationRequest> findHistory(@Param("verifierId") UUID verifierId,
                                               @Param("statuses") Collection<LegalVerificationStatus> statuses,
                                               @Param("fromTime") LocalDateTime fromTime,
                                               @Param("toTime") LocalDateTime toTime,
                                               Pageable pageable);

    // FR-12: id hồ sơ đã APPROVED nhưng chưa ký và quá hạn (decidedAt < cutoff)
    @Query("""
        SELECT r.id FROM LegalVerificationRequest r
        WHERE r.status = :status AND r.decidedAt < :cutoff
          AND NOT EXISTS (SELECT 1 FROM DigitalSignature s WHERE s.request = r)
        ORDER BY r.decidedAt ASC
    """)
    List<UUID> findExpiredUnsignedIds(@Param("status") LegalVerificationStatus status,
                                      @Param("cutoff") LocalDateTime cutoff,
                                      Pageable pageable);

    // Khóa bi quan khi ký: tránh 2 request ký song song → lỗi 500
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM LegalVerificationRequest r WHERE r.id = :id")
    Optional<LegalVerificationRequest> findByIdForUpdate(@Param("id") UUID id);
}