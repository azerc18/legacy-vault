package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.VerificationExchangeMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

import java.util.List;
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
}
