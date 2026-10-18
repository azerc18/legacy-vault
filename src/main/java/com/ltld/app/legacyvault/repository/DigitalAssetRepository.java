package com.ltld.app.legacyvault.repository;
import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.enums.AssetStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
@Repository
public interface DigitalAssetRepository extends JpaRepository<DigitalAsset, UUID> {
    List<DigitalAsset> findByVaultId(UUID vaultId);

    // FR-08: tài sản của nhiều Vault trong một truy vấn (tránh N+1)
    @Query("SELECT a FROM DigitalAsset a WHERE a.vault.id IN :vaultIds AND a.status = :status")
    List<DigitalAsset> findByVaultIdInAndStatus(@Param("vaultIds") Collection<UUID> vaultIds, @Param("status") AssetStatus status);

    // FR-19: đếm tài sản đang hoạt động của Vault (precondition đóng hồ sơ)
    long countByVaultIdAndStatus(UUID vaultId, AssetStatus status);

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

    // BR-04: khi lưu trữ chỉ giữ metadata (tên, loại, trạng thái, tham chiếu khóa).
    // encrypted_secret là NOT NULL nên ghi chuỗi rỗng; bulk UPDATE bỏ qua @UpdateTimestamp nên set updatedAt tay.
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE DigitalAsset a
            SET a.encryptedSecret = '', a.notesEncrypted = NULL, a.attachmentUrl = NULL, a.updatedAt = :now
            WHERE a.vault.id = :vaultId
            """)
    int wipeContentByVaultId(@Param("vaultId") UUID vaultId, @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM DigitalAsset a WHERE a.vault.id = :vaultId")
    int deleteByVaultId(@Param("vaultId") UUID vaultId);
}
