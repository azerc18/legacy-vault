package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.VaultStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
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

    // FR-08: Vault của Owner kèm Beneficiary (LEFT JOIN FETCH tránh N+1)
    @Query("SELECT v FROM Vault v LEFT JOIN FETCH v.beneficiary WHERE v.owner.id = :ownerId ORDER BY v.createdAt ASC, v.id ASC")
    List<Vault> findByOwnerIdWithBeneficiary(@Param("ownerId") UUID ownerId);

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

    // FR-16/FR-17: danh sách vault được bàn giao cho một Beneficiary (mới mở khóa trước).
    // EntityGraph nạp luôn owner để hiển thị tên Owner mà không phát sinh thêm truy vấn cho từng vault.
    @EntityGraph(attributePaths = {"owner"})
    List<Vault> findByBeneficiaryIdAndStatusInOrderByUnlockedAtDescNameAscIdAsc(UUID beneficiaryId,
                                                                    Collection<VaultStatus> statuses);

    // BR-04: Vault UNLOCKED đã quá hạn nhận
    @Query("""
            SELECT v.id FROM Vault v
            WHERE v.status = com.ltld.app.legacyvault.enums.VaultStatus.UNLOCKED
              AND v.claimDeadlineAt < :now
            ORDER BY v.claimDeadlineAt ASC
            """)
    List<UUID> findExpiredUnlockedVaultIds(@Param("now") LocalDateTime now, Pageable pageable);

    // UNLOCKED -> ARCHIVED_LOCKED nguyên tử: vault vừa CLAIMED thì nhận 0 dòng bị ảnh hưởng và bị bỏ qua
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Vault v
            SET v.status = com.ltld.app.legacyvault.enums.VaultStatus.ARCHIVED_LOCKED,
                v.archivedAt = :now, v.purgeEligibleAt = :purgeAt, v.updatedAt = :now
            WHERE v.id = :id
              AND v.status = com.ltld.app.legacyvault.enums.VaultStatus.UNLOCKED
              AND v.claimDeadlineAt < :now
            """)
    int archiveIfExpired(@Param("id") UUID id, @Param("now") LocalDateTime now,
                         @Param("purgeAt") LocalDateTime purgeAt);

    // FR-06 (Q26): email này có đang là người thụ hưởng (đã liên kết hoặc đang chờ đăng ký) của Vault nào của Owner không
    @Query("""
           select count(v) > 0 from Vault v left join v.beneficiary b
           where v.owner.id = :ownerId
             and (lower(b.email) = :email or lower(v.beneficiaryEmail) = :email)
           """)
    boolean existsBeneficiaryWithEmail(@Param("ownerId") UUID ownerId, @Param("email") String email);
}
