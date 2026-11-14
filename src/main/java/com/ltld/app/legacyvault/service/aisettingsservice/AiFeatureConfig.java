package com.ltld.app.legacyvault.service.aisettingsservice;

/**
 * Cấu hình đang có hiệu lực của một chức năng AI: đã gộp lựa chọn của Admin với mặc định ở biến môi trường.
 *
 * @param backupModel null hoặc trùng model chính thì không có bước dự phòng
 */
public record AiFeatureConfig(boolean enabled, String primaryModel, String backupModel) {
}
