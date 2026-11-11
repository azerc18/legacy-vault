package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.PaymentTransaction;
import com.ltld.app.legacyvault.enums.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {

    @EntityGraph(attributePaths = {"plan"})
    List<PaymentTransaction> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /** Số thứ tự hoá đơn mô phỏng = số giao dịch SUCCESS đã thanh toán tới thời điểm này (không cần cột mới). */
    long countByStatusAndPaidAtLessThanEqual(PaymentStatus status, LocalDateTime paidAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"user", "plan"})
    @Query("SELECT p FROM PaymentTransaction p WHERE p.id = :id")
    Optional<PaymentTransaction> findByIdForUpdate(@Param("id") UUID id);

    /** Đơn PENDING tạo trước mốc {@code before}, cũ nhất trước (job đối soát). */
    @Query("SELECT p.id FROM PaymentTransaction p WHERE p.status = :status AND p.createdAt < :before ORDER BY p.createdAt")
    List<UUID> findIdsByStatusAndCreatedAtBefore(@Param("status") PaymentStatus status,
                                                @Param("before") LocalDateTime before,
                                                Pageable pageable);

    /**
     * Đơn EXPIRED chưa thu tiền, đóng vì lý do {@code reason} (lưu ở vnpResponseCode, vd. SUPERSEDED), tạo sau mốc
     * {@code since}: job hỏi lại cổng phòng khi đã trả mà IPN bị lỡ. Mới nhất trước, vì đơn mới dễ là đơn vừa trả.
     */
    @Query("SELECT p.id FROM PaymentTransaction p WHERE p.status = :status AND p.paidAt IS NULL"
            + " AND p.vnpResponseCode = :reason AND p.createdAt >= :since ORDER BY p.createdAt DESC")
    List<UUID> findUnpaidIdsByStatusAndReasonSince(@Param("status") PaymentStatus status,
                                                   @Param("reason") String reason,
                                                   @Param("since") LocalDateTime since,
                                                   Pageable pageable);

    List<PaymentTransaction> findByUserIdAndStatus(UUID userId, PaymentStatus status);

    // Việc 6: cổng của lần thanh toán thành công gần nhất, để "Gia hạn 1 chạm" chọn sẵn
    Optional<PaymentTransaction> findFirstByUserIdAndStatusOrderByPaidAtDesc(UUID userId, PaymentStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"user", "plan"})
    @Query("SELECT p FROM PaymentTransaction p WHERE p.gatewayTxnRef = :ref")
    Optional<PaymentTransaction> findByGatewayTxnRefForUpdate(@Param("ref") String ref);
}

