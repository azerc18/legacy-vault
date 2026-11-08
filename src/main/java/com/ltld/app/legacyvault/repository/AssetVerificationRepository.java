package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.AssetVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AssetVerificationRepository extends JpaRepository<AssetVerification, UUID> {

    Optional<AssetVerification> findFirstByAssetIdOrderByCreatedAtDesc(UUID assetId);
}
