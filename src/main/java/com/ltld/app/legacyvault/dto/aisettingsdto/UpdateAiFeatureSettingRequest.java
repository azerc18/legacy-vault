package com.ltld.app.legacyvault.dto.aisettingsdto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Admin đổi cấu hình một chức năng AI. Model để trống / null = quay về mặc định của biến môi trường.
 * Model phải nằm trong danh sách cho phép; không có trường nào cho API key.
 */
public record UpdateAiFeatureSettingRequest(
        @NotNull Boolean enabled,
        @Size(max = 100) String primaryModel,
        @Size(max = 100) String backupModel,
        // AI dự phòng; fallbackEnabled null = giữ nguyên. Provider: GROQ | DEEPSEEK; trống = mặc định cấu hình.
        Boolean fallbackEnabled,
        @Size(max = 20) String textFallbackProvider,
        @Size(max = 100) String textFallbackModel,
        @Size(max = 100) String visionFallbackModel) {

    public UpdateAiFeatureSettingRequest(Boolean enabled, String primaryModel, String backupModel) {
        this(enabled, primaryModel, backupModel, null, null, null, null);
    }
}
