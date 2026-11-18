package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.SigningKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SigningKeyRepository extends JpaRepository<SigningKey, UUID> {

    Optional<SigningKey> findFirstByActiveTrueOrderByCreatedAtDesc();

    Optional<SigningKey> findByKeyRef(String keyRef);
}
