package com.ltld.app.legacyvault.dto.beneficiarydto;

import com.ltld.app.legacyvault.enums.AssetType;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class InheritedAssetSummaryResponse {
    private UUID id;
    private AssetType assetType;
    private String assetName;
}