-- Xác minh danh tính Owner (KYC) trước khi bật DMS lần đầu.
-- Số CCCD / CMND chỉ lưu bản mã hoá; cột *_hash là HMAC (khoá ngoài DB) để tra trùng mà không cần giải mã.
-- Không lưu ảnh CCCD và địa chỉ thường trú, chỉ lưu tỉnh nơi ở.
CREATE TABLE `owner_identities` (
  `id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `citizen_id_encrypted` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL,
  `citizen_id_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `citizen_id_masked` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `old_id_encrypted` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `old_id_hash` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `full_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `date_of_birth` date NOT NULL,
  `gender` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `residence_province` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `ai_ocr_consent` tinyint(1) NOT NULL DEFAULT 0,
  `consent_at` datetime(6) NOT NULL,
  `verified_at` datetime(6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_owner_identity_user` (`user_id`),
  UNIQUE KEY `uk_owner_identity_citizen_hash` (`citizen_id_hash`),
  KEY `idx_owner_identity_old_id_hash` (`old_id_hash`),
  CONSTRAINT `fk_owner_identity_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Mã OTP xác minh số điện thoại: mỗi người một dòng, gửi lại thì ghi đè.
CREATE TABLE `phone_otp_challenges` (
  `user_id` binary(16) NOT NULL,
  `phone_e164` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `code_hash` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `sent_at` datetime(6) NOT NULL,
  `attempts` int NOT NULL DEFAULT 0,
  `window_started_at` datetime(6) NOT NULL,
  `window_send_count` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`user_id`),
  CONSTRAINT `fk_phone_otp_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `users`
  ADD COLUMN `phone_verified_at` datetime(6) DEFAULT NULL;
