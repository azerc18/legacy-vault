package com.ltld.app.legacyvault.dto.identitydto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class PhoneOtpResponse {
    private String maskedPhone;
    private String carrier;
    private Instant expiresAt;
    private long resendAfterSeconds;
    // Chỉ có khi chạy chế độ SMS mô phỏng (chưa nối nhà cung cấp SMS thật)
    private String sandboxCode;
}
