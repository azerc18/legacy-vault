package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.VerificationMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VerificationMessageRepository extends JpaRepository<VerificationMessage, UUID> {

    List<VerificationMessage> findByRequestIdOrderBySentAtAsc(UUID requestId);
}