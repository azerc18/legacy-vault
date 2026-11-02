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

    // findPendingByEmail
    @Query("SELECT b FROM VaultBeneficiary b WHERE b.user IS NULL AND b.email = :email")
    List<VaultBeneficiary> findPendingByEmail(@Param("email") String email);

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

    long countByVaultId(UUID vaultId);   // N2-owner: PUT cũ chỉ hợp lệ khi vault có ≤ 1 người

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
