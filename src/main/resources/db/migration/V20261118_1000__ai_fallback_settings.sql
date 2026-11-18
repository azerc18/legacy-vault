-- AI dự phòng khi Gemini lỗi, Admin chỉnh ở trang Cấu hình AI (API key vẫn chỉ ở biến môi trường).
-- fallback_enabled: cho phép dùng Groq / DeepSeek khi Gemini hỏng.
-- text_fallback_provider / text_fallback_model: chat Vaulty và PDF có chữ (GROQ | DEEPSEEK); NULL = mặc định cấu hình.
-- vision_fallback_model: model Groq đọc ảnh (JPG/PNG, PDF scan); NULL = GROQ_VISION_MODEL.
ALTER TABLE `ai_feature_settings`
  ADD COLUMN `fallback_enabled` tinyint(1) NOT NULL DEFAULT 1,
  ADD COLUMN `text_fallback_provider` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD COLUMN `text_fallback_model` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD COLUMN `vision_fallback_model` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL;
