package com.ltld.app.legacyvault.dto.vaultdto;

import com.ltld.app.legacyvault.enums.AssetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

@Data
public class CreateVaultRequest {
    @NotBlank(message = "Tên két sắt không được để trống")
    @Size(max = 100, message = "Tên két sắt tối đa 100 ký tự")
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
