package com.ltld.app.legacyvault.service.vaultservice;

import com.ltld.app.legacyvault.dto.vaultdto.DigitalAssetDetailResponse;
import com.ltld.app.legacyvault.dto.vaultdto.DigitalAssetRequest;
import com.ltld.app.legacyvault.dto.vaultdto.DigitalAssetResponse;

import java.util.List;
import java.util.UUID;

/**
 * FR-27 – Owner quản lý tài sản số trong Vault của mình.
 * Tất cả method đều kiểm tra ownership; sai chủ → 404.
 */
public interface DigitalAssetService {

    /**
     * FR-27: Xem danh sách tài sản ACTIVE của một Vault.
     * Không trả secret.
     */
    List<DigitalAssetResponse> listAssets(UUID ownerId, UUID vaultId);

    /**
     * FR-27: Xem chi tiết 1 tài sản (bao gồm giải mã secret).
     * Ghi audit ASSET_VIEWED.
     */
    DigitalAssetDetailResponse getAsset(UUID ownerId, UUID vaultId, UUID assetId) throws Exception;

    /**
     * FR-27: Thêm tài sản mới vào Vault đang tồn tại.
     * Bị chặn khi DMS triggered hoặc verification request đang mở (qua VaultModificationGuard).
     * Ghi audit ASSET_CREATED sau khi lưu DB thành công.
     */
    DigitalAssetResponse addAsset(UUID ownerId, UUID vaultId, DigitalAssetRequest request) throws Exception;

    /**
     * FR-27: Cập nhật thông tin tài sản (tên, loại, secret mới, ghi chú).
     * Bị chặn khi DMS triggered hoặc verification request đang mở.
     * Ghi audit ASSET_UPDATED sau khi lưu DB thành công.
     */
    DigitalAssetResponse updateAsset(UUID ownerId, UUID vaultId, UUID assetId, DigitalAssetRequest request) throws Exception;

    /**
     * FR-27: Xóa cứng tài sản khỏi DB (hard-delete, nhất quán với deleteVault).
     * Xóa thành công và flush DB trước khi ghi audit ASSET_DELETED.
     * Bị chặn khi DMS triggered hoặc verification request đang mở.
     */
    void deleteAsset(UUID ownerId, UUID vaultId, UUID assetId);
}
