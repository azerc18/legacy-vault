package com.ltld.app.legacyvault.repository;
import com.ltld.app.legacyvault.entity.DigitalAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
@Repository
public interface DigitalAssetRepository extends JpaRepository<DigitalAsset, UUID> {
    List<DigitalAsset> findByVaultId(UUID vaultId);
}
