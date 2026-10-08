package com.ltld.app.legacyvault.dto.vaultdto;

import com.ltld.app.legacyvault.enums.AssetType;
import lombok.Data;
import java.util.List;

@Data
public class CreateVaultRequest {
    private String name;
    private String description;
    // Danh sách tài sản số sẽ lưu vào Vault
    private List<DigitalAssetDto> assets;

    @Data
    public static class DigitalAssetDto {
        private AssetType assetType;
        private String assetName;
        // Dữ liệu người dùng gõ vào (Text gốc)
        private String secret;
        private String notes;
    }
}
