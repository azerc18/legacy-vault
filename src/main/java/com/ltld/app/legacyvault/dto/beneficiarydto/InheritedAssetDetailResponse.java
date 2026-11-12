package com.ltld.app.legacyvault.dto.beneficiarydto;

import com.ltld.app.legacyvault.enums.AssetType;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class InheritedAssetDetailResponse {
    private UUID id;
    private AssetType assetType;
    private String assetName;
    // Đã giải mã tạm thời cho phiên xem, không bao giờ lưu lại bản rõ
    @ToString.Exclude
    private String secret;
    @ToString.Exclude
    private String notes;
    // Trường công khai của tài sản theo thứ tự schema (tên ngân hàng, số tài khoản, nơi giữ bản gốc...).
    // KHÔNG che số tài khoản: người nhận đã qua OTP/KYC và cần số đầy đủ để làm thủ tục. Không ghi log.
    @ToString.Exclude
    private Map<String, String> fields;

}