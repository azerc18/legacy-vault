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
}
