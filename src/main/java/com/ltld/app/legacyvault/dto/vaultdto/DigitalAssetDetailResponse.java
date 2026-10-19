package com.ltld.app.legacyvault.dto.vaultdto;

import com.ltld.app.legacyvault.enums.AssetStatus;
import com.ltld.app.legacyvault.enums.AssetType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response trả về cho Owner khi xem chi tiết 1 tài sản (V2).
 * Bao gồm secret đã giải mã và notes đã giải mã — chỉ Owner của vault được xem.
 *
 * TODO (cần hỏi leader): Có nên thêm cơ chế xác nhận (OTP/password) trước khi trả secret không?
 *   - Phương án A: Trả thẳng qua HTTPS (hiện tại) — đơn giản, đủ với JWT.
 *   - Phương án B: Yêu cầu OTP trước khi giải mã — bảo mật hơn nhưng UX phức tạp.
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
