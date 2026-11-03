package com.ltld.app.legacyvault.service.dmsservice;

import com.ltld.app.legacyvault.dto.dmsdto.OneClickCheckinResponse;

import java.util.UUID;

/**
 * F2: Check-in Dead Man's Switch một chạm qua link trong email nhắc.
 * Link mở trang FE /dms/checkin?token=... (mở trang không đổi trạng thái); Owner bấm nút thì FE gọi
 * POST /api/dms/checkin-link. Token: purpose=DMS_CHECKIN, audience riêng, hạn 7 ngày, jti dùng 1 lần,
 * claim cyc gắn với chu kỳ check-in lúc sinh.
 */
public interface DmsOneClickCheckinService {

    /**
     * Sinh token check-in cho Owner của DmsConfig (dùng trong mail nhắc). cyc = lastCheckinAt hiện tại.
     * Không có DmsConfig -> null (mail vẫn gửi, không kèm link).
     */
    String generateCheckinTokenForConfig(UUID dmsConfigId);

    /**
     * Xác thực token (chữ ký, issuer, audience, hạn, purpose, jti, subject) rồi check-in qua
     * {@link DmsConfigService#checkInFromEmailLink}. Lỗi -> DmsCheckinLinkException kèm reason.
     */
    OneClickCheckinResponse checkInWithToken(String rawToken);
}
