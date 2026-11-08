package com.ltld.app.legacyvault.service.assetverificationservice;

import com.ltld.app.legacyvault.dto.assetverificationdto.AssetVerificationResponse;
import com.ltld.app.legacyvault.dto.assetverificationdto.AssetVerificationSummary;
import com.ltld.app.legacyvault.dto.assetverificationdto.ConfirmVerificationRequest;
import com.ltld.app.legacyvault.dto.assetverificationdto.StartVerificationRequest;
import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.AssetVerificationMethod;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface AssetVerificationService {

    AssetVerificationResponse getStatus(UUID ownerId, UUID vaultId, UUID assetId);

    AssetVerificationResponse start(UUID ownerId, UUID vaultId, UUID assetId, StartVerificationRequest request);

    AssetVerificationResponse confirm(UUID ownerId, UUID vaultId, UUID assetId, ConfirmVerificationRequest request);

    /**
     * Trạng thái xác thực HIỆU LỰC của nhiều tài sản trong 1 lần (1 query). Mọi tài sản đều có mặt trong kết quả;
     * chưa xác thực, bị sửa trường danh tính hoặc không có dòng nào đều là SELF_DECLARED.
     */
    Map<UUID, AssetVerificationSummary> summarize(Collection<DigitalAsset> assets);

    /** Các cách xác thực theo loại tài sản (cho GET /api/asset-types). */
    Map<AssetType, List<AssetVerificationMethod>> methodsByType();
}
