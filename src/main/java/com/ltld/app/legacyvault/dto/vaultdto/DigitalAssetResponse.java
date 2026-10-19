package com.ltld.app.legacyvault.dto.vaultdto;

import com.ltld.app.legacyvault.enums.AssetStatus;
import com.ltld.app.legacyvault.enums.AssetType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response trả về cho Owner khi xem danh sách tài sản (V1).
 * KHÔNG chứa encryptedSecret, không chứa đường dẫn file.
 * Xem chi tiết (V2) dùng DigitalAssetDetailResponse để trả thêm secret đã giải mã.
 */
@Getter
@Builder
public class DigitalAssetResponse {
    private final UUID id;
    private final AssetType assetType;
    private final String assetName;
    private final AssetStatus status;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
}
