package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.dto.entity.User;
import com.ltld.app.legacyvault.dto.entity.VerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {
    Optional<VerificationToken> findTopByUserAndIsUsedFalseOrderByCreatedAtDesc(User user);
}
