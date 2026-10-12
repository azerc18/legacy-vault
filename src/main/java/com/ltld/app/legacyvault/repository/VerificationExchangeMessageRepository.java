package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.VerificationExchangeMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VerificationExchangeMessageRepository extends JpaRepository<VerificationExchangeMessage, UUID> {

    List<VerificationExchangeMessage> findAllByVerificationIdOrderByCreatedAtAsc(UUID verificationId);
}
