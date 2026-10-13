package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.Vault;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface VaultRepository extends JpaRepository<Vault, UUID> {

    // FR-16: Vault UNLOCKED có Beneficiary, còn hạn nhận, mà chưa gửi được thông báo
    // (chưa có claim, hoặc claim chưa có notifiedAt và còn lượt thử).
    // unlockedBefore: bỏ qua Vault vừa mở khóa để không đụng với listener đang xử lý sự kiện.
    @Query("""
            SELECT v.id FROM Vault v
            LEFT JOIN BeneficiaryClaim c ON c.vault = v
            WHERE v.status = com.ltld.app.legacyvault.enums.VaultStatus.UNLOCKED
              AND v.beneficiary IS NOT NULL
              AND v.claimDeadlineAt > :now
              AND (v.unlockedAt IS NULL OR v.unlockedAt <= :unlockedBefore)
              AND (c.id IS NULL OR (c.notifiedAt IS NULL AND c.notifyAttempts < :maxAttempts))
            """)
    List<UUID> findVaultIdsToNotify(@Param("now") LocalDateTime now,
                                    @Param("unlockedBefore") LocalDateTime unlockedBefore,
                                    @Param("maxAttempts") int maxAttempts);
}