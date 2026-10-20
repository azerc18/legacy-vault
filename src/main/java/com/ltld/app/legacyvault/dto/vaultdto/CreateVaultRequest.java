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

    @Size(max = 500, message = "Mô tả két sắt tối đa 500 ký tự")
    private String description;

    // FR-01: Danh sách tài sản số sẽ lưu vào Vault (tối đa 50, validate từng phần tử)
    @Size(max = 50, message = "Số lượng tài sản tối đa 50")
    private List<@Valid DigitalAssetDto> assets;

    @Data
    public static class DigitalAssetDto {
        @NotNull(message = "Loại tài sản không được để trống")
        private AssetType assetType;

        @NotBlank(message = "Tên tài sản không được để trống")
        @Size(max = 200, message = "Tên tài sản tối đa 200 ký tự")
        private String assetName;

        // FR-01: tài sản tạo kèm Vault phải có secret (cột encrypted_secret NOT NULL)
        @NotBlank(message = "Secret không được để trống")
        @Size(max = 5000, message = "Secret tối đa 5000 ký tự")
        private String secret;

        @Size(max = 2000, message = "Ghi chú tối đa 2000 ký tự")
        private String notes;
    }
}
