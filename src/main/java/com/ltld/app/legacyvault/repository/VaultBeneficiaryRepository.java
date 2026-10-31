package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.VaultBeneficiary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface VaultBeneficiaryRepository extends JpaRepository<VaultBeneficiary, UUID> {

    List<VaultBeneficiary> findByVaultIdOrderByCreatedAtAsc(UUID vaultId);

    List<VaultBeneficiary> findByVaultIdInOrderByCreatedAtAsc(Collection<UUID> vaultIds);

    // Owner đã mời email này ở bất kỳ vault nào của mình chưa (dùng cho N2-Q26, N2-verifier)
    @Query("""
            SELECT COUNT(b) > 0 FROM VaultBeneficiary b
            WHERE b.vault.owner.id = :ownerId AND LOWER(b.email) = LOWER(:email)
            """)
    boolean existsByOwnerIdAndEmail(@Param("ownerId") UUID ownerId, @Param("email") String email);

    // Người được mời khi chưa có tài khoản, dùng khi họ xác minh email xong
    @Query("SELECT b FROM VaultBeneficiary b WHERE b.user IS NULL AND LOWER(b.email) = LOWER(:email)")
    List<VaultBeneficiary> findPendingByEmail(@Param("email") String email);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM VaultBeneficiary b WHERE b.vault.id = :vaultId")
    int deleteByVaultId(@Param("vaultId") UUID vaultId);
}