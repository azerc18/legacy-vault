package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.VaultBeneficiary;
import org.springframework.data.jpa.repository.JpaRepository;
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
public interface VaultBeneficiaryRepository extends JpaRepository<VaultBeneficiary, UUID> {

    List<VaultBeneficiary> findByVaultIdOrderByCreatedAtAsc(UUID vaultId);

    List<VaultBeneficiary> findByVaultIdInOrderByCreatedAtAsc(Collection<UUID> vaultIds);

    // existsByOwnerIdAndEmail
    @Query("""
        SELECT COUNT(b) > 0 FROM VaultBeneficiary b
        WHERE b.vault.owner.id = :ownerId AND b.email = :email
        """)
    boolean existsByOwnerIdAndEmail(@Param("ownerId") UUID ownerId, @Param("email") String email);

    // N25: người thụ hưởng còn chưa nhận xong của các két UNLOCKED có hạn trong [from, to)
    @Query("""
        SELECT b FROM VaultBeneficiary b JOIN FETCH b.vault v JOIN FETCH v.owner
        WHERE v.status = com.ltld.app.legacyvault.enums.VaultStatus.UNLOCKED
          AND v.claimDeadlineAt >= :from AND v.claimDeadlineAt < :to
          AND b.status NOT IN (com.ltld.app.legacyvault.enums.BeneficiaryInviteStatus.DECLINED,
                               com.ltld.app.legacyvault.enums.BeneficiaryInviteStatus.WAIVED)
          AND NOT EXISTS (SELECT c FROM BeneficiaryClaim c WHERE c.vault = v AND c.beneficiary = b.user
                          AND c.status IN (com.ltld.app.legacyvault.enums.ClaimStatus.CLAIMED,
                                           com.ltld.app.legacyvault.enums.ClaimStatus.DECLINED))
        """)
    List<VaultBeneficiary> findUnclaimedWithDeadlineBetween(@Param("from") java.time.LocalDateTime from,
                                                            @Param("to") java.time.LocalDateTime to);

    // findPendingByEmail
    @Query("SELECT b FROM VaultBeneficiary b WHERE b.user IS NULL AND b.email = :email")
    List<VaultBeneficiary> findPendingByEmail(@Param("email") String email);

    // N2-verifier: verifier là beneficiary của bất kỳ vault nào trong hồ sơ (theo tài khoản hoặc email)
    @Query("""
            SELECT COUNT(b) > 0 FROM VaultBeneficiary b
            WHERE b.vault.id IN (SELECT rv.vault.id FROM VerificationRequestVault rv WHERE rv.request.id = :requestId)
              AND (b.user.id = :verifierId
                   OR LOWER(b.email) = (SELECT LOWER(u.email) FROM User u WHERE u.id = :verifierId))
            """)
    boolean isBeneficiaryOfRequest(@Param("requestId") UUID requestId, @Param("verifierId") UUID verifierId);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM VaultBeneficiary b WHERE b.vault.id = :vaultId")
    int deleteByVaultId(@Param("vaultId") UUID vaultId);

    boolean existsByVaultIdAndEmail(UUID vaultId, String email);

    // N2b: tìm dòng thụ hưởng của một user trong một vault (cổng truy cập của luồng claim)
    Optional<VaultBeneficiary> findByVaultIdAndUserId(UUID vaultId, UUID userId);

    // N2b: trạng thái phản hồi của một user trong nhiều vault (một query, tránh N+1 ở listMyVaults)
    @Query("SELECT b FROM VaultBeneficiary b WHERE b.user.id = :userId AND b.vault.id IN :vaultIds")
    List<VaultBeneficiary> findByUserIdAndVaultIdIn(@Param("userId") UUID userId,
                                                    @Param("vaultIds") Collection<UUID> vaultIds);

    long countByVaultId(UUID vaultId);

    /** Việc 2: số người thụ hưởng của từng vault thuộc Owner (để biết vault đông nhất so với hạn mức gói). */
    @Query("SELECT COUNT(b) FROM VaultBeneficiary b WHERE b.vault.owner.id = :ownerId GROUP BY b.vault.id")
    List<Long> countPerVaultByOwnerId(@Param("ownerId") UUID ownerId);   // N2-owner: PUT cũ chỉ hợp lệ khi vault có ≤ 1 người

    // Xoá tài khoản: còn là người thụ hưởng (đã gắn tài khoản) ở két chưa hoàn tất nào không,
    // trừ dòng đã từ chối / nhường. Két CLAIMED / ARCHIVED_LOCKED đã xong nên không chặn.
    @Query("""
            SELECT COUNT(b) > 0 FROM VaultBeneficiary b
            WHERE b.user.id = :userId
              AND b.status NOT IN (com.ltld.app.legacyvault.enums.BeneficiaryInviteStatus.DECLINED,
                                   com.ltld.app.legacyvault.enums.BeneficiaryInviteStatus.WAIVED)
              AND b.vault.status IN (com.ltld.app.legacyvault.enums.VaultStatus.ACTIVE,
                                     com.ltld.app.legacyvault.enums.VaultStatus.UNLOCKED)
            """)
    boolean existsLiveLinkForUser(@Param("userId") UUID userId);

    // N2-exec: ghi lần nhắc nếu lần trước đã quá cutoff; trả 0 khi chưa đủ 24h (một câu UPDATE, không cần khóa)
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE VaultBeneficiary vb SET vb.lastReminderAt = :now
            WHERE vb.id = :id AND (vb.lastReminderAt IS NULL OR vb.lastReminderAt <= :cutoff)
            """)
    int markReminded(@Param("id") UUID id, @Param("now") LocalDateTime now, @Param("cutoff") LocalDateTime cutoff);

    // F1: id các vault của Owner có ít nhất 1 người thụ hưởng (một query cho cả Owner, tránh N+1)
    @Query("SELECT DISTINCT b.vault.id FROM VaultBeneficiary b WHERE b.vault.owner.id = :ownerId")
    List<UUID> findVaultIdsWithBeneficiaryByOwnerId(@Param("ownerId") UUID ownerId);
}
