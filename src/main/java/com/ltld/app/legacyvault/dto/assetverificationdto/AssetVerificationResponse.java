package com.ltld.app.legacyvault.dto.assetverificationdto;

import com.ltld.app.legacyvault.enums.AssetVerificationMethod;
import com.ltld.app.legacyvault.enums.AssetVerificationStatus;
import com.ltld.app.legacyvault.enums.VerificationProvider;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Builder
public class AssetVerificationResponse {
    private final UUID assetId;
    private final AssetVerificationStatus status;
    private final AssetVerificationMethod method;       // null nếu SELF_DECLARED
    private final VerificationProvider provider;        // null nếu SELF_DECLARED
    private final LocalDateTime verifiedAt;
    /** PENDING: hạn của thử thách; VERIFIED: hạn hiệu lực của xác thực. */
    private final LocalDateTime expiresAt;
    /** Các cách xác thực loại tài sản này hỗ trợ (rỗng khi chưa có verifier). */
    private final List<AssetVerificationMethod> availableMethods;
    /** Thông báo cho người dùng (ví dụ "Đã gửi mã", "Sai mã"). */
    private final String message;
    /** Dữ liệu verifier muốn FE hiển thị sau bước start (ví dụ thông điệp cần ký). Không chứa bí mật. */
    private final Map<String, String> clientData;
}
