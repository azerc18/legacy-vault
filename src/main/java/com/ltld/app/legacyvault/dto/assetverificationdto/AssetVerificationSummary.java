package com.ltld.app.legacyvault.dto.assetverificationdto;

import com.ltld.app.legacyvault.enums.AssetVerificationMethod;
import com.ltld.app.legacyvault.enums.AssetVerificationStatus;
import com.ltld.app.legacyvault.enums.VerificationProvider;

import java.time.LocalDateTime;

/** Trạng thái xác thực HIỆU LỰC của một tài sản (đã tính dấu vân tay và hạn dùng), dùng để gắn huy hiệu. */
public record AssetVerificationSummary(AssetVerificationStatus status,
                                       AssetVerificationMethod method,
                                       VerificationProvider provider,
                                       LocalDateTime verifiedAt,
                                       LocalDateTime expiresAt) {
}
