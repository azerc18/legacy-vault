package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.AssetVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssetVerificationRepository extends JpaRepository<AssetVerification, UUID> {

    Optional<AssetVerification> findFirstByAssetIdOrderByCreatedAtDesc(UUID assetId);

    /** Dòng xác thực mới nhất của từng tài sản trong danh sách (1 query, tránh N+1). */
    @Query("""
            SELECT v FROM AssetVerification v
            WHERE v.asset.id IN :assetIds
              AND v.createdAt = (SELECT MAX(v2.createdAt) FROM AssetVerification v2 WHERE v2.asset.id = v.asset.id)
            """)
    List<AssetVerification> findLatestByAssetIds(@Param("assetIds") Collection<UUID> assetIds);
}
