package com.ltld.app.legacyvault.dto.beneficiarydto;

import com.ltld.app.legacyvault.enums.AllocationMode;
import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.BeneficiaryInviteStatus;
import com.ltld.app.legacyvault.enums.VaultStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// N2-notify: tiến độ nhận bàn giao cho màn hình ClaimProgress. Chỉ metadata, không giải mã gì.
// Tài sản chỉ gồm phần của người đang gọi (cùng quy tắc N2-access), số người chỉ là con số, không lộ danh tính.
@Data
@Builder
public class ClaimProgressResponse {
    private UUID vaultId;
    private VaultStatus vaultStatus;
    private BeneficiaryInviteStatus myStatus;
    private LocalDateTime claimDeadlineAt;

    // Số người thụ hưởng còn nhận được (không tính đã từ chối / từ bỏ)
    private int totalBeneficiaries;
    private int acceptedBeneficiaries;
    private int declinedBeneficiaries;
    // Số người đã đóng hồ sơ phần của mình
    private int closedBeneficiaries;

    // Phần của người đang gọi
    private int totalAssets;
    private int viewedAssets;
    private List<AssetProgress> assets;

    @Data
    @Builder
    public static class AssetProgress {
        private UUID assetId;
        private AssetType assetType;
        private String assetName;
        // null = tài sản chưa có dòng phân bổ riêng (mọi người thụ hưởng còn nhận được đều thấy, Q47 b)
        private AllocationMode allocationMode;
        // Chỉ có khi PROPORTIONAL: phần trăm của người đang gọi
        private BigDecimal sharePercent;
        // true nếu phần này vốn của người khác và đã chuyển sang mình do fallback
        private boolean reassignedToMe;
        private boolean viewed;
    }
}