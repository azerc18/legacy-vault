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

    boolean existsByBeneficiaryId(UUID beneficiaryId);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE AssetAllocation al SET al.contingentBeneficiary = NULL WHERE al.contingentBeneficiary.id = :bid")
    int clearContingentByBeneficiaryId(@Param("bid") UUID bid);
}
