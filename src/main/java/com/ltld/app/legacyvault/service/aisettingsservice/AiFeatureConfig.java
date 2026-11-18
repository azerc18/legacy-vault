package com.ltld.app.legacyvault.service.aisettingsservice;

import com.ltld.app.legacyvault.enums.AiFallbackProvider;

/**
 * Cấu hình đang có hiệu lực của một chức năng AI: đã gộp lựa chọn của Admin với mặc định ở biến môi trường.
 *
 * @param backupModel          null hoặc trùng model chính thì không có bước dự phòng Gemini
 * @param fallbackEnabled      cho phép chuyển sang Groq / DeepSeek khi Gemini hỏng
 * @param textFallbackProvider nhà cung cấp cho văn bản (chat, PDF có chữ); null = mặc định cấu hình
 * @param textFallbackModel    model văn bản; null = mặc định của nhà cung cấp
 * @param visionFallbackModel  model Groq đọc ảnh; null = mặc định cấu hình
 */
public record AiFeatureConfig(boolean enabled, String primaryModel, String backupModel,
                              boolean fallbackEnabled, AiFallbackProvider textFallbackProvider,
                              String textFallbackModel, String visionFallbackModel) {

    /** Chỉ có Gemini (dự phòng Groq / DeepSeek theo mặc định cấu hình). */
    public AiFeatureConfig(boolean enabled, String primaryModel, String backupModel) {
        this(enabled, primaryModel, backupModel, true, null, null, null);
    }
}
