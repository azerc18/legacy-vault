package com.ltld.app.legacyvault.repository;
import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.enums.AssetStatus;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
