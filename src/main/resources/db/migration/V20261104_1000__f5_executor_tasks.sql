-- F5: checklist Executor xử lý tài khoản số (MXH / email / ví điện tử) theo di nguyện, sinh khi Vault mở khóa
CREATE TABLE `executor_tasks` (
  `id` binary(16) NOT NULL,
  `vault_id` binary(16) NOT NULL,
  `asset_id` binary(16) NOT NULL,
  `platform` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `account_handle` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `wish` enum('DELETE','MEMORIALIZE','TRANSFER_DATA') COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('DOING','DONE','TODO') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'TODO',
  `note` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_executor_task_asset` (`asset_id`),
  KEY `idx_executor_task_vault` (`vault_id`),
  CONSTRAINT `fk_executor_task_vault` FOREIGN KEY (`vault_id`) REFERENCES `vaults` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_executor_task_asset` FOREIGN KEY (`asset_id`) REFERENCES `digital_assets` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
