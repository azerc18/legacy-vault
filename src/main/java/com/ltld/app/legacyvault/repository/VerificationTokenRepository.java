package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.VerificationToken;
import com.ltld.app.legacyvault.enums.TokenType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {
    Optional<VerificationToken> findTopByUserAndTypeAndIsUsedFalseOrderByCreatedAtDesc(User user, TokenType type);
    long countByUserAndTypeAndCreatedAtAfter(User user, TokenType type, Instant since);
}
