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
    @NotBlank(message = "Tên két sắt không được để trống")
    @Size(max = 100, message = "Tên két sắt tối đa 100 ký tự")
    private String name;

    @Size(max = 1000, message = "Mô tả két sắt tối đa 1000 ký tự")
    private String description;

    // Danh sách tài sản số sẽ lưu vào Vault (n9: thêm @Valid)
    private List<@Valid DigitalAssetDto> assets;

    @Data
    public static class DigitalAssetDto {
        @NotNull(message = "Loại tài sản không được để trống")
        private AssetType assetType;

        @NotBlank(message = "Tên tài sản không được để trống")
        @Size(max = 200, message = "Tên tài sản tối đa 200 ký tự")
        private String assetName;

        // Dữ liệu người dùng gõ vào (Text gốc)
        @Size(max = 5000, message = "Secret tối đa 5000 ký tự")
        private String secret;

        @Size(max = 2000, message = "Ghi chú tối đa 2000 ký tự")
        private String notes;
    }
}
