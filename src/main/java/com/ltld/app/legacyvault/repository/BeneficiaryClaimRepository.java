package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.BeneficiaryClaim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BeneficiaryClaimRepository extends JpaRepository<BeneficiaryClaim, UUID> {
    // Tìm kiếm hồ sơ claim dựa trên id của Vault
    Optional<BeneficiaryClaim> findByVaultId(UUID vaultId);

    // Chuyển PENDING -> CLAIMED nguyên tử: request thứ hai nhận 0 dòng bị ảnh hưởng nên bị từ chối
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE BeneficiaryClaim c
            SET c.status = com.ltld.app.legacyvault.enums.ClaimStatus.CLAIMED, c.claimedAt = :now
            WHERE c.id = :id AND c.status = com.ltld.app.legacyvault.enums.ClaimStatus.PENDING
            """)
    int markClaimed(@Param("id") UUID id, @Param("now") LocalDateTime now);
}