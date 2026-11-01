package com.ltld.app.legacyvault.dto.vaultdto;

import com.ltld.app.legacyvault.enums.AllocationMode;
import com.ltld.app.legacyvault.enums.AssetStatus;
import com.ltld.app.legacyvault.enums.AssetType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Response trả về cho Owner khi xem danh sách tài sản (FR-27).
 * KHÔNG chứa encryptedSecret, không chứa đường dẫn file.
 * Xem chi tiết dùng DigitalAssetDetailResponse để trả thêm secret đã giải mã.
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
    private final List<Allocation> allocations;   // rỗng = mặc định thuộc tất cả

    @Getter
    @Builder
    public static class Allocation {
        private final UUID beneficiaryId;
        private final UUID contingentBeneficiaryId;
        private final AllocationMode mode;
        private final BigDecimal sharePercent;
        private final String note;
    }
}
