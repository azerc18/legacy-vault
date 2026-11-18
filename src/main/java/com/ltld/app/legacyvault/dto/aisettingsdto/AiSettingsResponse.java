package com.ltld.app.legacyvault.dto.aisettingsdto;

import java.util.List;

/**
 * Trang Cấu hình AI. Chỉ cho biết key đã được đặt hay chưa, KHÔNG BAO GIỜ trả giá trị key.
 *
 * @param provider          giá trị AI_PROVIDER (gemini / mock)
 * @param apiKeyConfigured  GEMINI_API_KEY có giá trị
 * @param allowedModels     danh sách model Admin được chọn (từ cấu hình, không nhập tự do)
 */
public record AiSettingsResponse(
        String provider,
        boolean apiKeyConfigured,
        boolean backupApiKeyConfigured,
        String defaultPrimaryModel,
        String defaultBackupModel,
        List<String> allowedModels,
        List<AiFeatureSettingResponse> features) {
}
