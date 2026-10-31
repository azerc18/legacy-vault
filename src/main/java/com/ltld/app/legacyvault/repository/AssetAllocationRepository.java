package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.AssetAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface AssetAllocationRepository extends JpaRepository<AssetAllocation, UUID> {

    List<AssetAllocation> findByAssetId(UUID assetId);

    List<AssetAllocation> findByAssetIdIn(Collection<UUID> assetIds);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM AssetAllocation al WHERE al.asset.id IN (SELECT a.id FROM DigitalAsset a WHERE a.vault.id = :vaultId)")
    int deleteByVaultId(@Param("vaultId") UUID vaultId);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM AssetAllocation al WHERE al.asset.id = :assetId")
    int deleteByAssetId(@Param("assetId") UUID assetId);

    // N2b: các phân bổ mà một người thụ hưởng đang giữ quyền nhận
    // (người chính chưa bị thay thế, hoặc được chuyển tới qua fallback)
    @Query("""
            SELECT al FROM AssetAllocation al
            WHERE al.asset.vault.id = :vaultId
              AND ((al.effectiveBeneficiary IS NULL AND al.beneficiary.id = :holderId)
                   OR al.effectiveBeneficiary.id = :holderId)
            """)
    List<AssetAllocation> findHeldBy(@Param("vaultId") UUID vaultId, @Param("holderId") UUID holderId);
}
