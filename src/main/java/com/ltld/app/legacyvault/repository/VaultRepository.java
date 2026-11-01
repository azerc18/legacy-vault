package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.VaultStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
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
    // N2b: khóa dòng vault. Luôn khóa vault trước rồi mới đụng tới claim để mọi nơi cùng thứ tự khóa
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Vault v WHERE v.id = :id")
    Optional<Vault> findByIdForUpdate(@Param("id") UUID id);

    // FR-02: các Vault đang chờ người thụ hưởng (theo email) đăng ký/xác minh tài khoản
    List<Vault> findByBeneficiaryEmailAndBeneficiaryIsNull(String beneficiaryEmail);

    // FR-03: Owner phải có ít nhất 1 Vault mới được cấu hình DMS.
    boolean existsByOwnerId(UUID ownerId);

    // FR-08: Vault của Owner kèm Beneficiary (LEFT JOIN FETCH tránh N+1)
    @Query("SELECT v FROM Vault v LEFT JOIN FETCH v.beneficiary WHERE v.owner.id = :ownerId ORDER BY v.createdAt ASC, v.id ASC")
    List<Vault> findByOwnerIdWithBeneficiary(@Param("ownerId") UUID ownerId);

    // FR-27: danh sách Vault của Owner (mới nhất trước) kèm Beneficiary, tránh N+1
    @Query("SELECT v FROM Vault v LEFT JOIN FETCH v.beneficiary WHERE v.owner.id = :ownerId "
            + "ORDER BY v.createdAt DESC, v.id DESC")
    List<Vault> findByOwnerIdWithBeneficiaryNewestFirst(@Param("ownerId") UUID ownerId);

    // FR-16: Vault UNLOCKED có Beneficiary, còn hạn nhận, mà chưa gửi được thông báo
    // (chưa có claim, hoặc claim chưa có notifiedAt, còn lượt thử và đã qua khoảng chờ tối thiểu).
    // unlockedBefore: bỏ qua Vault vừa mở khóa để không đụng với listener đang xử lý sự kiện.
    // lastAttemptBefore: lọc thô theo khoảng chờ nhỏ nhất; khoảng chờ chính xác kiểm ở listener.
    @Query("""
            SELECT v.id FROM Vault v
            WHERE v.status = com.ltld.app.legacyvault.enums.VaultStatus.UNLOCKED
              AND v.claimDeadlineAt > :now
              AND (v.unlockedAt IS NULL OR v.unlockedAt <= :unlockedBefore)
              AND EXISTS (
                  SELECT 1 FROM VaultBeneficiary b
                  LEFT JOIN BeneficiaryClaim c ON c.vault = v AND c.beneficiary = b.user
                  WHERE b.vault = v
                    AND b.user IS NOT NULL
                    AND b.status NOT IN (com.ltld.app.legacyvault.enums.BeneficiaryInviteStatus.DECLINED,
                                         com.ltld.app.legacyvault.enums.BeneficiaryInviteStatus.WAIVED)
                    AND (c.id IS NULL OR (c.status = com.ltld.app.legacyvault.enums.ClaimStatus.PENDING
                         AND c.notifiedAt IS NULL
                         AND c.notifyAttempts < :maxAttempts
                         AND (c.lastNotifyAttemptAt IS NULL OR c.lastNotifyAttemptAt <= :lastAttemptBefore))))
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

    // N2b: vault được bàn giao cho một người dùng (qua vault_beneficiaries thay vì cột vaults.beneficiary_id)
    @EntityGraph(attributePaths = {"owner"})
    @Query("""
            SELECT v FROM Vault v
            WHERE v.status IN :statuses
              AND EXISTS (SELECT 1 FROM VaultBeneficiary b WHERE b.vault = v AND b.user.id = :userId)
            ORDER BY v.unlockedAt DESC, v.name ASC, v.id ASC
            """)
    List<Vault> findVaultsOfBeneficiary(@Param("userId") UUID userId,
                                        @Param("statuses") Collection<VaultStatus> statuses);

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

    // Lấy danh sách Vault của Owner (mới nhất xếp trước)
    List<Vault> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    // Admin tìm kiếm và lọc Vault theo trạng thái
    Page<Vault> findByStatus(VaultStatus status, Pageable pageable);

    // FR-06 (Q26): email này có đang là người thụ hưởng (đã liên kết hoặc đang chờ đăng ký) của Vault nào của Owner không
    @Query("""
           select count(v) > 0 from Vault v left join v.beneficiary b
           where v.owner.id = :ownerId
             and (lower(b.email) = :email or lower(v.beneficiaryEmail) = :email)
           """)
    boolean existsBeneficiaryWithEmail(@Param("ownerId") UUID ownerId, @Param("email") String email);

    // BR-05: Vault đã lưu trữ và tới hạn xóa vĩnh viễn
    @Query("""
            SELECT v.id FROM Vault v
            WHERE v.status = com.ltld.app.legacyvault.enums.VaultStatus.ARCHIVED_LOCKED
              AND v.purgeEligibleAt <= :now
            ORDER BY v.purgeEligibleAt ASC
            """)
    List<UUID> findPurgeableVaultIds(@Param("now") LocalDateTime now, Pageable pageable);
}
