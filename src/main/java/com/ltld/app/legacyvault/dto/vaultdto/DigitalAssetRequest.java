package com.ltld.app.legacyvault.dto.vaultdto;

import com.ltld.app.legacyvault.enums.AllocationMode;
import com.ltld.app.legacyvault.enums.AssetType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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

    // N2-owner: null = giữ nguyên (sửa) / mặc định tất cả (tạo); recipients rỗng = xóa phân bổ riêng
    @Valid
    private AllocationRequest allocation;

    @Data
    public static class AllocationRequest {
        private AllocationMode mode;
        @Valid
        private List<Recipient> recipients = new ArrayList<>();
        @Size(max = 1000, message = "Allocation note must be at most 1000 characters")
        private String note;
    }

    @Data
    public static class Recipient {
        @NotNull(message = "beneficiaryId is required")
        private UUID beneficiaryId;
        private BigDecimal sharePercent;          // bắt buộc với PROPORTIONAL
        private UUID contingentBeneficiaryId;     // chỉ dùng với EXCLUSIVE
    }
}
