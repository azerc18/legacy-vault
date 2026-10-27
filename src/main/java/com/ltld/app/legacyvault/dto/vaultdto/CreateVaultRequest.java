package com.ltld.app.legacyvault.dto.vaultdto;

import com.ltld.app.legacyvault.enums.AssetType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

@Data
public class CreateVaultRequest {
    @NotBlank(message = "Vault name is required")
    @Size(max = 100, message = "Vault name must be at most 100 characters")
    private String name;

    @Size(max = 500, message = "Vault description must be at most 500 characters")
    private String description;

    // FR-01: Danh sách tài sản số sẽ lưu vào Vault (tối đa 50, validate từng phần tử)
    @Size(max = 50, message = "At most 50 assets are allowed")
    private List<@Valid DigitalAssetDto> assets;

    @Data
    public static class DigitalAssetDto {
        @NotNull(message = "Asset type is required")
        private AssetType assetType;

        @NotBlank(message = "Asset name is required")
        @Size(max = 200, message = "Asset name must be at most 200 characters")
        private String assetName;

        // FR-01: tài sản tạo kèm Vault phải có secret (cột encrypted_secret NOT NULL)
        @NotBlank(message = "Secret is required")
        @Size(max = 5000, message = "Secret must be at most 5000 characters")
        private String secret;

        @Size(max = 2000, message = "Note must be at most 2000 characters")
        private String notes;
    }
}
