package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.AssetAllocation;
import com.ltld.app.legacyvault.enums.BeneficiaryInviteStatus;
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

    // F1: id các tài sản ACTIVE của Owner đã có ít nhất 1 dòng phân bổ tường minh (một query cho cả Owner)
    @Query("""
            SELECT DISTINCT al.asset.id FROM AssetAllocation al
            WHERE al.asset.vault.owner.id = :ownerId
              AND al.asset.status = com.ltld.app.legacyvault.enums.AssetStatus.ACTIVE
            """)
    List<UUID> findAllocatedActiveAssetIdsByOwnerId(@Param("ownerId") UUID ownerId);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE AssetAllocation al SET al.contingentBeneficiary = NULL WHERE al.contingentBeneficiary.id = :bid")
    int clearContingentByBeneficiaryId(@Param("bid") UUID bid);

    // N2b: các phân bổ mà một người thụ hưởng đang giữ quyền nhận
    // (người chính chưa bị thay thế, hoặc được chuyển tới qua fallback)
    @Query("""
            SELECT al FROM AssetAllocation al
            WHERE al.asset.vault.id = :vaultId
              AND ((al.effectiveBeneficiary IS NULL AND al.beneficiary.id = :holderId)
                   OR al.effectiveBeneficiary.id = :holderId)
            """)
    List<AssetAllocation> findHeldBy(@Param("vaultId") UUID vaultId, @Param("holderId") UUID holderId);

    // N2b (Q49): số phân bổ mà người đang giữ đã từ chối / từ bỏ, tức không còn ai nhận (stalled)
    // LEFT JOIN tường minh: nếu dùng al.effectiveBeneficiary.status thì Hibernate tạo INNER JOIN và làm mất
    // các dòng chưa fallback (effectiveBeneficiary IS NULL).
    @Query("""
            SELECT COUNT(al) FROM AssetAllocation al
            JOIN al.beneficiary b
            LEFT JOIN al.effectiveBeneficiary e
            WHERE al.asset.vault.id = :vaultId
              AND ((e IS NULL AND b.status IN :deadStatuses) OR e.status IN :deadStatuses)
            """)
    long countStalledInVault(@Param("vaultId") UUID vaultId,
                             @Param("deadStatuses") Collection<BeneficiaryInviteStatus> deadStatuses);

    // N2-exec: mọi phân bổ của một vault, để biết phần của ai đã chuyển sang người dự phòng
    @Query("SELECT al FROM AssetAllocation al WHERE al.asset.vault.id = :vaultId")
    List<AssetAllocation> findByVaultId(@Param("vaultId") UUID vaultId);
}
