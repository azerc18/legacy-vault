package com.ltld.app.legacyvault.dto.vaultdto;

import com.ltld.app.legacyvault.enums.AssetStatus;
import com.ltld.app.legacyvault.enums.AssetType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response trả về cho Owner khi xem chi tiết 1 tài sản (FR-27).
 * Bao gồm secret đã giải mã và notes đã giải mã — chỉ Owner của vault được xem.
 * Q36: Owner xem secret của tài sản bằng JWT hợp lệ qua HTTPS; mỗi lần xem ghi audit ASSET_VIEWED
 * (hiển thị trong nhật ký Vault); response không được cache (no-store). Không yêu cầu OTP.
 */
@Getter
@Builder
public class DigitalAssetDetailResponse {
    private final UUID id;
    private final AssetType assetType;
    private final String assetName;
    /**
     * Secret đã giải mã — plain-text. Chỉ trả khi Owner xem chi tiết.
     * FE cần xử lý an toàn, không log, không cache.
     */
    private final String secret;
    /** Ghi chú đã giải mã (có thể null). */
    private final String notes;
    private final AssetStatus status;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
}
