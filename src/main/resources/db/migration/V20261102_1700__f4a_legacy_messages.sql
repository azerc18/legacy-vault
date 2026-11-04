-- F4a: Loi nhan di nguyen & Hen gio tuong lai (Time-Capsule)
CREATE TABLE `legacy_messages` (
  `id` binary(16) NOT NULL,
  `vault_id` binary(16) NOT NULL,
  `recipient_type` enum('ALL_BENEFICIARIES','SPECIFIC_BENEFICIARY') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ALL_BENEFICIARIES',
  `recipient_beneficiary_id` binary(16) DEFAULT NULL,
  `title` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `content` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `delivery_trigger` enum('ON_CLAIM','SPECIFIC_DATE') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ON_CLAIM',
  `deliver_date` date DEFAULT NULL,
  `status` enum('ACTIVE','DELIVERED') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  `attachment_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `attachment_path` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `attachment_type` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `attachment_size` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `delivered_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_legacy_msg_vault` (`vault_id`),
  KEY `idx_legacy_msg_recipient` (`recipient_beneficiary_id`),
  KEY `idx_legacy_msg_schedule` (`status`,`delivery_trigger`,`deliver_date`),
  CONSTRAINT `fk_legacy_msg_vault` FOREIGN KEY (`vault_id`) REFERENCES `vaults` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_legacy_msg_recipient` FOREIGN KEY (`recipient_beneficiary_id`) REFERENCES `vault_beneficiaries` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

