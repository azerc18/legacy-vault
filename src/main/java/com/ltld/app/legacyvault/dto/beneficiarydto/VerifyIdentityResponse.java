package com.ltld.app.legacyvault.dto.beneficiarydto;

import com.ltld.app.legacyvault.enums.VerificationStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class VerifyIdentityResponse {
    private UUID verificationId;
    private VerificationStatus status;
    private LocalDateTime verifiedAt;
    // Hết thời điểm này thì phải xác thực lại mới xem được tài sản
    private LocalDateTime viewSessionExpiresAt;
}