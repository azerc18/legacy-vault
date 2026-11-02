-- X2: người xác nhận chéo DMS (Trusted Social Confirmers) và trạng thái trung gian PENDING_CONFIRMATION.
-- dms_configs.status là MySQL enum (baseline): MODIFY phải liệt kê ĐỦ giá trị cũ, thêm PENDING_CONFIRMATION ở cuối.
-- AuditAction mới của X2 không cần migration: audit_logs.action đã là VARCHAR (V20261101_1100).

ALTER TABLE `dms_configs` MODIFY COLUMN `status`
    enum('ACTIVE','SUSPENDED','TRIGGERED','PENDING_CONFIRMATION') COLLATE utf8mb4_unicode_ci NOT NULL;

CREATE TABLE `dms_confirmers` (
  `created_at` datetime(6) NOT NULL,
  `responded_at` datetime(6) DEFAULT NULL,
  `dms_config_id` binary(16) NOT NULL,
  `id` binary(16) NOT NULL,
  `relationship` varchar(80) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `last_token_jti` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `name` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `email` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('ACTIVE','CONFIRMED_ALIVE','CONFIRMED_UNREACHABLE') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dms_confirmer_config_email` (`dms_config_id`,`email`),
  UNIQUE KEY `uk_dms_confirmer_last_token_jti` (`last_token_jti`),
  CONSTRAINT `fk_dms_confirmers_config` FOREIGN KEY (`dms_config_id`) REFERENCES `dms_configs` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
