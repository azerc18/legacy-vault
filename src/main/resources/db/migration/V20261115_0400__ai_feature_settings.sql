-- Admin bật / tắt từng chức năng dùng AI và chọn model (trong danh sách cho phép ở cấu hình).
-- API key KHÔNG lưu ở đây: vẫn chỉ đặt qua biến môi trường GEMINI_API_KEY / GEMINI_BACKUP_API_KEY.
-- primary_model / backup_model NULL = dùng model mặc định của biến môi trường (GEMINI_MODEL / GEMINI_BACKUP_MODEL).
CREATE TABLE `ai_feature_settings` (
  `feature` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT 1,
  `primary_model` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `backup_model` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `updated_by` binary(16) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`feature`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `ai_feature_settings` (`feature`, `enabled`) VALUES
  ('ASSISTANT', 1),
  ('DEATH_CERTIFICATE_OCR', 1),
  ('CITIZEN_ID_OCR', 1);
