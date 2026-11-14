package com.ltld.app.legacyvault.dto.aisettingsdto;

import java.time.LocalDateTime;

/**
 * Một dòng ở trang Cấu hình AI.
 *
 * @param primaryModel    model đang dùng thật (lựa chọn của Admin, hoặc mặc định nếu Admin chưa chọn)
 * @param primaryOverride lựa chọn Admin đã lưu; null = theo mặc định biến môi trường
 */
public record AiFeatureSettingResponse(
        String feature,
        boolean enabled,
        String primaryModel,
        String backupModel,
        String primaryOverride,
        String backupOverride,
        LocalDateTime updatedAt,
        String updatedByEmail) {
}
