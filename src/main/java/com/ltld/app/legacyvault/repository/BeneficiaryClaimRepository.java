package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.BeneficiaryClaim;
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
public interface BeneficiaryClaimRepository extends JpaRepository<BeneficiaryClaim, UUID> {
    // Tìm kiếm hồ sơ claim dựa trên id của Vault
    Optional<BeneficiaryClaim> findByVaultId(UUID vaultId);

    // FR-16/FR-17: lấy claim của nhiều vault trong một truy vấn (cho danh sách vault của Beneficiary)
    List<BeneficiaryClaim> findByVaultIdIn(Collection<UUID> vaultIds);

    Optional<BeneficiaryClaim> findByVaultIdAndBeneficiaryId(UUID vaultId, UUID beneficiaryId);

    List<BeneficiaryClaim> findByBeneficiaryIdAndVaultIdIn(UUID beneficiaryId, Collection<UUID> vaultIds);

    // Chuyển PENDING -> CLAIMED nguyên tử: request thứ hai nhận 0 dòng bị ảnh hưởng nên bị từ chối
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE BeneficiaryClaim c
            SET c.status = com.ltld.app.legacyvault.enums.ClaimStatus.CLAIMED, c.claimedAt = :now
            WHERE c.id = :id AND c.status = com.ltld.app.legacyvault.enums.ClaimStatus.PENDING
            """)
    int markClaimed(@Param("id") UUID id, @Param("now") LocalDateTime now);

    // BR-04: PENDING -> EXPIRED nguyên tử; claim đã CLAIMED thì không bị đụng
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE BeneficiaryClaim c
            SET c.status = com.ltld.app.legacyvault.enums.ClaimStatus.EXPIRED
            WHERE c.vault.id = :vaultId AND c.status = com.ltld.app.legacyvault.enums.ClaimStatus.PENDING
            """)
    int markExpiredByVaultId(@Param("vaultId") UUID vaultId);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM BeneficiaryClaim c WHERE c.vault.id = :vaultId")
    int deleteByVaultId(@Param("vaultId") UUID vaultId);
}