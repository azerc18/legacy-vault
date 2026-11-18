-- V3: chữ ký xác nhận của Legal Verifier kiểm lại được.
-- signing_keys: cặp khóa ECDSA P-256 của hệ thống. Khóa riêng mã hóa bằng CRYPTO_MASTER_KEY (AES-256-GCM),
-- khóa công khai để ai cũng kiểm lại được chữ ký. Chỉ một khóa active tại một thời điểm.
CREATE TABLE `signing_keys` (
  `id` binary(16) NOT NULL,
  `key_ref` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `algorithm` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `public_key_pem` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `private_key_encrypted` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `fingerprint_sha256` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `active` tinyint(1) NOT NULL DEFAULT 1,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_signing_keys_key_ref` (`key_ref`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Chữ ký cũ (trước PR này) để NULL: FE hiện "chữ ký cũ, không kiểm lại được".
ALTER TABLE `digital_signatures`
  ADD COLUMN `document_sha256` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD COLUMN `signed_payload` text COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD COLUMN `signature_value` text COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD COLUMN `signature_algorithm` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD COLUMN `signing_key_id` binary(16) DEFAULT NULL,
  ADD CONSTRAINT `fk_digital_signatures_signing_key` FOREIGN KEY (`signing_key_id`) REFERENCES `signing_keys` (`id`);
