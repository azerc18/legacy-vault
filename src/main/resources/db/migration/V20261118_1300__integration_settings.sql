-- Trang "Tích hợp" của Admin: các cờ tích hợp Admin bật / tắt được ngay, không cần sửa biến môi trường rồi deploy lại.
-- Chỉ có 1 dòng (id = 1).
-- kyc_mock_enabled: eKYC mô phỏng cho người thụ hưởng (MockKycVerifier). NULL = theo biến môi trường APP_KYC_MOCK_ENABLED
-- (app.kyc.mock.enabled) như trước; 1 / 0 = Admin đã chọn bật / tắt, ghi đè biến môi trường.
-- Không có cột nào chứa key / secret: các khoá tích hợp vẫn chỉ đặt bằng biến môi trường.
CREATE TABLE `integration_settings` (
  `id` tinyint NOT NULL,
  `kyc_mock_enabled` tinyint(1) DEFAULT NULL,
  `updated_by` binary(16) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `integration_settings` (`id`) VALUES (1);
