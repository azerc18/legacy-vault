package com.ltld.app.legacyvault.dto.beneficiarydto;

import com.ltld.app.legacyvault.enums.ClaimStatus;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

// N2b: kết quả khi Beneficiary từ chối phần được chia
@Getter
@Builder
public class DeclineClaimResponse {
    private final UUID vaultId;
    private final ClaimStatus claimStatus;
    // Số tài sản đã chuyển sang người dự phòng
    private final int reassignedAssets;
    // Số tài sản chưa có người nhận (không có người dự phòng còn nhận được)
    private final int stalledAssets;
}