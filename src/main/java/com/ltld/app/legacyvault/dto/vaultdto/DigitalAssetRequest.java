package com.ltld.app.legacyvault.dto.vaultdto;

import com.ltld.app.legacyvault.enums.AssetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request body dùng cho POST (thêm mới) và PUT (cập nhật) digital asset.
 * Phần secret / notes là plain-text; service sẽ mã hóa trước khi lưu.
 */
@Data
public class DigitalAssetRequest {

    @NotNull(message = "Loại tài sản không được để trống")
    private AssetType assetType;

    @NotBlank(message = "Tên tài sản không được để trống")
    @Size(max = 200, message = "Tên tài sản tối đa 200 ký tự")
    private String assetName;

    /**
     * Secret dạng plain-text (password, private key, seed phrase…).
     * Bắt buộc khi tạo mới; khi cập nhật có thể bỏ qua (null = giữ nguyên).
     */
    @Size(max = 5000, message = "Secret tối đa 5000 ký tự")
    private String secret;

    @Size(max = 2000, message = "Ghi chú tối đa 2000 ký tự")
    private String notes;
}
