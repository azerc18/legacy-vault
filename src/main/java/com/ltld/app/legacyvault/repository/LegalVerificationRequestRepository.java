package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.LegalVerificationRequest;
import com.ltld.app.legacyvault.enums.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LegalVerificationRequestRepository extends JpaRepository<LegalVerificationRequest, UUID> {

    List<LegalVerificationRequest> findByStatus(VerificationStatus status);

    List<LegalVerificationRequest> findByVerifierId(UUID verifierId);

    List<LegalVerificationRequest> findByOwnerId(UUID ownerId);
}