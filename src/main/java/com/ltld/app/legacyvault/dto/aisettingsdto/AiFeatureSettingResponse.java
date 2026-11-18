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
        String updatedByEmail,
        // AI dự phòng: giá trị đang có hiệu lực, và lựa chọn Admin đã lưu (null = mặc định)
        boolean fallbackEnabled,
        String textFallbackProvider,
        String textFallbackModel,
        String visionFallbackModel,
        String textFallbackProviderOverride,
        String textFallbackModelOverride,
        String visionFallbackModelOverride) {

    public AiFeatureSettingResponse(String feature, boolean enabled, String primaryModel, String backupModel,
                                    String primaryOverride, String backupOverride, LocalDateTime updatedAt,
                                    String updatedByEmail) {
        this(feature, enabled, primaryModel, backupModel, primaryOverride, backupOverride, updatedAt, updatedByEmail,
                true, null, null, null, null, null, null);
    }
}
