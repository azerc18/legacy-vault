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
        List<AiFeatureSettingResponse> features,
        Fallback fallback) {

    public AiSettingsResponse(String provider, boolean apiKeyConfigured, boolean backupApiKeyConfigured,
                              String defaultPrimaryModel, String defaultBackupModel, List<String> allowedModels,
                              List<AiFeatureSettingResponse> features) {
        this(provider, apiKeyConfigured, backupApiKeyConfigured, defaultPrimaryModel, defaultBackupModel,
                allowedModels, features, null);
    }

    /**
     * AI dự phòng (Groq / DeepSeek). Chỉ cho biết key đã đặt hay chưa, không trả giá trị key.
     *
     * @param groqVisionModels  model Groq đọc được ảnh (cho ô "Model đọc ảnh"), tách khỏi model văn bản
     * @param defaultTextModels model văn bản mặc định theo nhà cung cấp (GROQ, DEEPSEEK)
     */
    public record Fallback(boolean groqKeyConfigured,
                           boolean deepseekKeyConfigured,
                           List<String> groqModels,
                           List<String> deepseekModels,
                           List<String> groqVisionModels,
                           String defaultTextProvider,
                           java.util.Map<String, String> defaultTextModels,
                           String defaultVisionModel) {
    }
}
