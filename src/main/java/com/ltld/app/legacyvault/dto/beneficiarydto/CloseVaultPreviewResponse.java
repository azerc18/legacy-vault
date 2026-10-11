package com.ltld.app.legacyvault.dto.beneficiarydto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CloseVaultPreviewResponse {
    private int totalAssets;
    private int viewedCount;
    private int unviewedCount;
    // Chỉ thông tin tóm tắt, không giải mã gì
    private List<InheritedAssetSummaryResponse> unviewedAssets;
}