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
        @Size(max = 100) String backupModel) {
}
