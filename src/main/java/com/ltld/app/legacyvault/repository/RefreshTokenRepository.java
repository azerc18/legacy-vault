package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.RefreshToken;
import com.ltld.app.legacyvault.enums.RevokedReason;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken,UUID> {
    Optional<RefreshToken> findByTokenHash(String rawToken);

    // N6: phiên mới nhất và các User-Agent trước đó của một người dùng
    Optional<RefreshToken> findFirstByUser_IdOrderByCreatedAtDesc(UUID userId);

    @Query("SELECT DISTINCT t.userAgent FROM RefreshToken t WHERE t.user.id = :userId AND t.createdAt < :before AND t.userAgent IS NOT NULL")
    List<String> findEarlierUserAgents(@Param("userId") UUID userId, @Param("before") Instant before);
    @Modifying
    @Query("""
    UPDATE RefreshToken t
       SET t.revokedAt = :now, t.revokedReason = :reason
     WHERE t.user.id = :userId AND t.revokedAt IS NULL
""")
    int revokeAllActiveByUserId(@Param("userId") UUID userId,
                                @Param("now") Instant now,
                                @Param("reason") RevokedReason reason);

    // Trang Hồ sơ: thiết bị đang đăng nhập = refresh token chưa thu hồi, chưa hết hạn
    @Query("""
    SELECT t FROM RefreshToken t
     WHERE t.user.id = :userId AND t.revokedAt IS NULL AND t.expiresAt > :now
""")
    List<RefreshToken> findActiveByUserId(@Param("userId") UUID userId, @Param("now") Instant now);

    // Trang Hồ sơ: "Đăng xuất mọi thiết bị khác" (giữ lại token của trình duyệt đang dùng)
    @Modifying
    @Query("""
    UPDATE RefreshToken t
       SET t.revokedAt = :now, t.revokedReason = :reason
     WHERE t.user.id = :userId AND t.revokedAt IS NULL AND t.tokenHash <> :keepHash
""")
    int revokeAllActiveByUserIdExcept(@Param("userId") UUID userId,
                                      @Param("keepHash") String keepHash,
                                      @Param("now") Instant now,
                                      @Param("reason") RevokedReason reason);
}
