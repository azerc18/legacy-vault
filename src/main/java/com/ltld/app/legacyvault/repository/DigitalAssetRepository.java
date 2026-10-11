package com.ltld.app.legacyvault.repository;
import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.enums.AssetStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
@Repository
public interface DigitalAssetRepository extends JpaRepository<DigitalAsset, UUID> {
    List<DigitalAsset> findByVaultId(UUID vaultId);

    // FR-17: chỉ lấy tài sản còn hiệu lực (loại REVOKED)
    List<DigitalAsset> findByVaultIdAndStatus(UUID vaultId, AssetStatus status);

    // FR-17: lấy 1 tài sản, bắt buộc thuộc đúng vault để không xem nhầm tài sản của vault khác
    Optional<DigitalAsset> findByIdAndVaultIdAndStatus(UUID id, UUID vaultId, AssetStatus status);

    // FR-19: tài sản ACTIVE của vault mà claim này chưa xem/tải lần nào
    @Query("""
            SELECT a FROM DigitalAsset a
            WHERE a.vault.id = :vaultId
              AND a.status = com.ltld.app.legacyvault.enums.AssetStatus.ACTIVE
              AND NOT EXISTS (SELECT 1 FROM AssetAccessRecord r
                              WHERE r.asset.id = a.id AND r.claim.id = :claimId)
            """)
    List<DigitalAsset> findUnaccessedAssets(@Param("vaultId") UUID vaultId, @Param("claimId") UUID claimId);
}
