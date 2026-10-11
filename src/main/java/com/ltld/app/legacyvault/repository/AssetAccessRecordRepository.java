package com.ltld.app.legacyvault.repository;

import com.ltld.app.legacyvault.entity.AssetAccessRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public interface AssetAccessRecordRepository extends JpaRepository<AssetAccessRecord, UUID> {

    // INSERT IGNORE: hai request xem cùng một tài sản đồng thời không được làm request nào bị lỗi.
    // Cặp (claim_id, asset_id) đã có thì bỏ qua, trả 0.
    @Modifying
    @Query(value = """
            INSERT IGNORE INTO asset_access_records (id, claim_id, asset_id, first_accessed_at)
            VALUES (:id, :claimId, :assetId, :now)
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("claimId") UUID claimId,
                       @Param("assetId") UUID assetId, @Param("now") LocalDateTime now);
}