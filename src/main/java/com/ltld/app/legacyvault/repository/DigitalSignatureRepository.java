package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.DigitalSignature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DigitalSignatureRepository extends JpaRepository<DigitalSignature, UUID> {

    Optional<DigitalSignature> findByRequestId(UUID requestId);

    boolean existsByRequestId(UUID requestId);
}