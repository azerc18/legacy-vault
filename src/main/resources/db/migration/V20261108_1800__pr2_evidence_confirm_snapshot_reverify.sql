-- PR 2 (C.4, C.6): tệp bằng chứng Executor, xác nhận chéo hai phía, bản chụp lúc bàn giao, nhắc xác thực lại

-- 1. Tệp bằng chứng của task Executor (mỗi task một tệp; tải lại thì thay tệp cũ)
CREATE TABLE `executor_evidence_files` (
  `id` binary(16) NOT NULL,
  `task_id` binary(16) NOT NULL,
  `vault_id` binary(16) NOT NULL,
  `file_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `file_url_encrypted` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `size_bytes` bigint DEFAULT NULL,
  `sha256` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `uploaded_by` binary(16) DEFAULT NULL,
  `uploaded_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_evidence_task` (`task_id`),
  KEY `idx_evidence_vault` (`vault_id`),
  CONSTRAINT `fk_evidence_task` FOREIGN KEY (`task_id`) REFERENCES `executor_tasks` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_evidence_vault` FOREIGN KEY (`vault_id`) REFERENCES `vaults` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. executor_tasks: thông tin tệp (chép lại để danh sách không phải JOIN) và xác nhận chéo
ALTER TABLE `executor_tasks`
  ADD COLUMN `evidence_file_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD COLUMN `evidence_sha256` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD COLUMN `evidence_uploaded_at` datetime(6) DEFAULT NULL,
  ADD COLUMN `confirmation_status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD COLUMN `confirm_deadline_at` datetime(6) DEFAULT NULL,
  ADD COLUMN `confirmed_at` datetime(6) DEFAULT NULL,
  ADD COLUMN `confirmed_by` binary(16) DEFAULT NULL,
  ADD COLUMN `dispute_reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD COLUMN `disputed_at` datetime(6) DEFAULT NULL,
  ADD COLUMN `disputed_by` binary(16) DEFAULT NULL;

-- 3. asset_verifications: đã nhắc "sắp hết hạn" chưa + chỉ mục cho scheduler
ALTER TABLE `asset_verifications`
  ADD COLUMN `reminded_at` datetime(6) DEFAULT NULL,
  ADD KEY `idx_asset_verification_status_expires` (`status`, `expires_at`);

-- 4. Bản chụp bất biến lúc bàn giao (mỗi Vault một bản)
CREATE TABLE `vault_handover_snapshots` (
  `id` binary(16) NOT NULL,
  `vault_id` binary(16) NOT NULL,
  `snapshot_data` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `snapshot_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `asset_count` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_handover_snapshot_vault` (`vault_id`),
  CONSTRAINT `fk_handover_snapshot_vault` FOREIGN KEY (`vault_id`) REFERENCES `vaults` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. Hai loại thông báo mới. Nếu main đã thêm loại khác thì GIỮ các loại đó trong danh sách ENUM.
ALTER TABLE `notifications`
  MODIFY COLUMN `type` ENUM('EXECUTOR_ACTIVATED','VAULT_UNLOCKED','VERIFICATION_DECIDED','VERIFIER_REPLY','ASSET_REASSIGNED','LEGACY_MESSAGE_DELIVERED','EXECUTOR_TASK_DISPUTED','ASSET_REVERIFY_NEEDED')
  COLLATE utf8mb4_unicode_ci NOT NULL;