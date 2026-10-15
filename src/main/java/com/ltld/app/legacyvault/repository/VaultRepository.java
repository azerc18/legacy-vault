package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.Vault;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VaultRepository extends JpaRepository<Vault, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Vault v where v.id = :id")
    Optional<Vault> findByIdForUpdate(@Param("id") UUID id);

    // FR-02: các Vault đang chờ người thụ hưởng (theo email) đăng ký/xác minh tài khoản
    List<Vault> findByBeneficiaryEmailAndBeneficiaryIsNull(String beneficiaryEmail);

    // FR-03: Owner phải có ít nhất 1 Vault mới được cấu hình DMS.
    boolean existsByOwnerId(UUID ownerId);

    // FR-16: Vault UNLOCKED có Beneficiary, còn hạn nhận, mà chưa gửi được thông báo
    // (chưa có claim, hoặc claim chưa có notifiedAt, còn lượt thử và đã qua khoảng chờ tối thiểu).
    // unlockedBefore: bỏ qua Vault vừa mở khóa để không đụng với listener đang xử lý sự kiện.
    // lastAttemptBefore: lọc thô theo khoảng chờ nhỏ nhất; khoảng chờ chính xác kiểm ở listener.
    @Query("""
            SELECT v.id FROM Vault v
            LEFT JOIN BeneficiaryClaim c ON c.vault = v
            WHERE v.status = com.ltld.app.legacyvault.enums.VaultStatus.UNLOCKED
              AND v.beneficiary IS NOT NULL
              AND v.claimDeadlineAt > :now
              AND (v.unlockedAt IS NULL OR v.unlockedAt <= :unlockedBefore)
              AND (c.id IS NULL OR (c.notifiedAt IS NULL
                   AND c.notifyAttempts < :maxAttempts
                   AND (c.lastNotifyAttemptAt IS NULL OR c.lastNotifyAttemptAt <= :lastAttemptBefore)))
            ORDER BY v.unlockedAt ASC
            """)
    List<UUID> findVaultIdsToNotify(@Param("now") LocalDateTime now,
                                    @Param("unlockedBefore") LocalDateTime unlockedBefore,
                                    @Param("lastAttemptBefore") LocalDateTime lastAttemptBefore,
                                    @Param("maxAttempts") int maxAttempts,
                                    Pageable pageable);
}
