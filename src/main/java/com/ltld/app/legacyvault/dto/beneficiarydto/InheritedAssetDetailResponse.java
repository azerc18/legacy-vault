package com.ltld.app.legacyvault.dto.beneficiarydto;

import com.ltld.app.legacyvault.enums.AssetType;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class InheritedAssetDetailResponse {
    private UUID id;
    private AssetType assetType;
    private String assetName;
    // Đã giải mã tạm thời cho phiên xem, không bao giờ lưu lại bản rõ
    private String secret;
    private String notes;
    private String attachmentUrl;
}