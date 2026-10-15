package com.ltld.app.legacyvault.dto.executordto;

import com.ltld.app.legacyvault.enums.AssetType;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class ExecutorVaultAssetResponse {
    private final UUID assetId;
    private final AssetType assetType;
    private final String assetName;
}
