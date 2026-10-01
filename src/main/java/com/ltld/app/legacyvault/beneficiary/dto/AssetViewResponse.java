package com.ltld.app.legacyvault.beneficiary.dto;

import lombok.Builder;
import lombok.Data;
import java.util.UUID;

@Data
@Builder
public class AssetViewResponse {
    private UUID vaultId;
    private String decryptedContent; // Nội dung đã giải mã tạm thời
    private String downloadUrl; // Đường dẫn để thực hiện FR-18
}