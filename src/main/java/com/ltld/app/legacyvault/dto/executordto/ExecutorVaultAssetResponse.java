package com.ltld.app.legacyvault.dto.executordto;

import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.AssetVerificationMethod;
import com.ltld.app.legacyvault.enums.AssetVerificationStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class ExecutorVaultAssetResponse {
    private final UUID assetId;
    private final AssetType assetType;
    private final String assetName;
    // C.5: huy hiệu để Executor biết mục nào đáng tin (nối dữ liệu ở bước "nền tảng")
    private final AssetVerificationStatus verificationStatus;
    private final AssetVerificationMethod verificationMethod;
    private final LocalDateTime verifiedAt;
}
