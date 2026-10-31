package com.ltld.app.legacyvault.service.dmsservice;

import com.ltld.app.legacyvault.dto.dmsdto.DmsConfigResponse;

import java.util.UUID;

/**
 * F2: Check-in Dead Man's Switch một chạm qua Email Token (purpose=DMS_CHECKIN, hạn 7 ngày, jti one-time).
 */
public interface DmsOneClickCheckinService {

    /** Sinh JWT token dùng 1 lần (purpose=DMS_CHECKIN, hạn 7 ngày, có jti duy nhất) cho Owner. */
    String generateCheckinToken(UUID ownerId);

    /** Tạo URL đầy đủ dẫn tới GET /api/dms/checkin-link?token=... cho Owner. */
    String buildCheckinLink(UUID ownerId);

    /** Tạo URL check-in 1 chạm từ dmsConfigId (dùng trong DmsReminderService). */
    String buildCheckinLinkForConfig(UUID dmsConfigId);

    /**
     * Xác thực token check-in 1 chạm, kiểm tra jti chưa từng dùng, reset chu kỳ DMS,
     * lưu DmsCheckin kèm tokenJti và ghi AuditLog DMS_CHECKIN_ONE_CLICK.
     * Token sai/hết hạn/đã dùng -> ném DmsException 400 CHECKIN_TOKEN_INVALID.
     */
    DmsConfigResponse checkInWithToken(String rawToken);

    /** Thực hiện checkInWithToken và trả về URL chuyển hướng tới trang FE /checkin/done. */
    String checkInAndBuildRedirectUrl(String rawToken);
}

