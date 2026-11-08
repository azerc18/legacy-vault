package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.AssetVerification;
import com.ltld.app.legacyvault.enums.AssetStatus;
import com.ltld.app.legacyvault.enums.AssetVerificationStatus;
import com.ltld.app.legacyvault.enums.VaultStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
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

    /**
     * Dòng xác thực MỚI NHẤT của từng tài sản đang còn giá trị, đã hết hạn hoặc sắp hết hạn trước cutoff
     * (chưa nhắc lần nào, hoặc đã hết hạn). Chỉ tài sản ACTIVE trong Vault ACTIVE (Owner còn sống).
     */
    @Query("""
            SELECT v FROM AssetVerification v
            JOIN FETCH v.asset a JOIN FETCH a.vault vt JOIN FETCH vt.owner
            WHERE v.status IN :statuses
              AND v.expiresAt < :cutoff
              AND (v.expiresAt < :now OR v.remindedAt IS NULL)
              AND a.status = :assetStatus
              AND vt.status = :vaultStatus
              AND v.createdAt = (SELECT MAX(v2.createdAt) FROM AssetVerification v2 WHERE v2.asset.id = v.asset.id)
            ORDER BY v.expiresAt ASC
            """)
    List<AssetVerification> findDueForReverification(@Param("statuses") Collection<AssetVerificationStatus> statuses,
                                                     @Param("cutoff") LocalDateTime cutoff,
                                                     @Param("now") LocalDateTime now,
                                                     @Param("assetStatus") AssetStatus assetStatus,
                                                     @Param("vaultStatus") VaultStatus vaultStatus,
                                                     Pageable pageable);
}
