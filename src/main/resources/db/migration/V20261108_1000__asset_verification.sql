-- C.5: lớp xác thực tài sản. status/method/provider lưu dạng chuỗi (như H0b) để thêm giá trị mới không cần ALTER ENUM
CREATE TABLE `asset_verifications` (
  `id` binary(16) NOT NULL,
  `asset_id` binary(16) NOT NULL,
  `method` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `provider` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `fingerprint` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `challenge_hash` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `evidence_json` text COLLATE utf8mb4_unicode_ci,
  `verified_at` datetime(6) DEFAULT NULL,
  `expires_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_asset_verification_asset` (`asset_id`),
  CONSTRAINT `fk_asset_verification_asset` FOREIGN KEY (`asset_id`) REFERENCES `digital_assets` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- C.4: bằng chứng hoàn tất cho checklist Executor (F5)
ALTER TABLE `executor_tasks`
  ADD COLUMN `evidence_ref` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD COLUMN `evidence_document_id` binary(16) DEFAULT NULL;
