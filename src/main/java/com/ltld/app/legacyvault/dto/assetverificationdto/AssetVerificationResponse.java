package com.ltld.app.legacyvault.dto.assetverificationdto;

import com.ltld.app.legacyvault.enums.AssetVerificationMethod;
import com.ltld.app.legacyvault.enums.AssetVerificationStatus;
import com.ltld.app.legacyvault.enums.VerificationProvider;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class AssetVerificationResponse {
    private final UUID assetId;
    private final AssetVerificationStatus status;
    private final AssetVerificationMethod method;       // null nếu SELF_DECLARED
    private final VerificationProvider provider;        // null nếu SELF_DECLARED
    private final LocalDateTime verifiedAt;
    private final LocalDateTime expiresAt;
    /** Các cách xác thực loại tài sản này hỗ trợ (rỗng khi chưa có verifier). */
    private final List<AssetVerificationMethod> availableMethods;
    private final String message;
}
