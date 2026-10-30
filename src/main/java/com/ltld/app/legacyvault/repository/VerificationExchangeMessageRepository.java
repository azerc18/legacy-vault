package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.VerificationExchangeMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VerificationExchangeMessageRepository extends JpaRepository<VerificationExchangeMessage, UUID> {

    // FR-11: lịch sử trao đổi của một yêu cầu xác minh, cũ nhất trước, kèm người gửi (một truy vấn)
    @Query("""
            SELECT m FROM VerificationExchangeMessage m JOIN FETCH m.sender
            WHERE m.request.id = :requestId
            ORDER BY m.createdAt ASC, m.id ASC
            """)
    List<VerificationExchangeMessage> findHistory(@Param("requestId") UUID requestId);

    Optional<VerificationExchangeMessage> findByIdAndRequestId(UUID id, UUID requestId);

    // FR-11: lịch sử phân trang cho Executor, thứ tự lấy từ Pageable
    @Query(value = """
            SELECT m FROM VerificationExchangeMessage m JOIN FETCH m.sender
            WHERE m.request.id = :requestId
            """,
            countQuery = "SELECT COUNT(m) FROM VerificationExchangeMessage m WHERE m.request.id = :requestId")
    Page<VerificationExchangeMessage> findHistoryPage(@Param("requestId") UUID requestId, Pageable pageable);

    // FR-11: số tin của Verifier mà Executor chưa đọc, theo từng hồ sơ: [requestId, count]
    @Query("""
            SELECT m.request.id, COUNT(m) FROM VerificationExchangeMessage m
            WHERE m.request.id IN :requestIds
              AND m.senderRole = com.ltld.app.legacyvault.enums.ExchangeSenderRole.VERIFIER
              AND (m.request.executorLastReadAt IS NULL OR m.createdAt > m.request.executorLastReadAt)
            GROUP BY m.request.id
            """)
    List<Object[]> countUnreadForExecutor(@Param("requestIds") Collection<UUID> requestIds);
}
