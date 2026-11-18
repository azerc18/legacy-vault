-- Chữ ký tay của Legal Verifier: phương thức ký thứ hai, ngang hàng với OTP qua email.
-- signature_image_encrypted: ảnh PNG (Base64) đã mã hóa AES-GCM bằng CRYPTO_MASTER_KEY, chỉ có khi ký tay.
-- signature_image_sha256: SHA-256 của ảnh PNG gốc; cũng nằm trong payload ECDSA nên thay ảnh sẽ bị phát hiện.
-- Version phải lớn hơn mọi migration trên main (mới nhất V20261118_1400).
ALTER TABLE `digital_signatures`
  MODIFY COLUMN `signature_method` enum('MOCK_OTP','MOCK_PIN','HANDWRITTEN') COLLATE utf8mb4_unicode_ci NOT NULL,
  ADD COLUMN `signature_image_encrypted` mediumtext COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD COLUMN `signature_image_sha256` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL;
