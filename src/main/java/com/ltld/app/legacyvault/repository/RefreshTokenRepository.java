package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.RefreshToken;
import com.ltld.app.legacyvault.enums.RevokedReason;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken,UUID> {
    Optional<RefreshToken> findByTokenHash(String rawToken);
    @Modifying
    @Query("""
    UPDATE RefreshToken t
       SET t.revokedAt = :now, t.revokedReason = :reason
     WHERE t.user.id = :userId AND t.revokedAt IS NULL
""")
    int revokeAllActiveByUserId(@Param("userId") UUID userId,
                                @Param("now") Instant now,
                                @Param("reason") RevokedReason reason);
}
