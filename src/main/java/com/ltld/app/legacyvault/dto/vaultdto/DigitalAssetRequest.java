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

    @NotNull(message = "Asset type is required")
    private AssetType assetType;

    @NotBlank(message = "Asset name is required")
    @Size(max = 200, message = "Asset name must be at most 200 characters")
    private String assetName;

    /**
     * Secret dạng plain-text (password, private key, seed phrase…).
     * Bắt buộc khi tạo mới; khi cập nhật có thể bỏ qua (null = giữ nguyên).
     */
    @Size(max = 5000, message = "Secret must be at most 5000 characters")
    private String secret;

    /**
     * Ghi chú bổ sung (hướng dẫn truy cập, mã PIN phụ...).
     * Khi cập nhật: null = giữ nguyên, chuỗi rỗng ("") = xóa ghi chú cũ, có giá trị = mã hóa giá trị mới.
     */
    @Size(max = 2000, message = "Note must be at most 2000 characters")
    private String notes;
}
