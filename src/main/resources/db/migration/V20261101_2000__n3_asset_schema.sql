-- N3: thêm 3 loại tài sản (EXCHANGE_ACCOUNT, E_WALLET, EMAIL) và cột lưu trường công khai theo schema từng loại
ALTER TABLE `digital_assets`
  MODIFY COLUMN `asset_type` enum('BANK_ACCOUNT','CRYPTO_WALLET','DOCUMENT','OTHER','SOCIAL_MEDIA','EXCHANGE_ACCOUNT','E_WALLET','EMAIL')
    COLLATE utf8mb4_unicode_ci NOT NULL,
  ADD COLUMN `public_fields_json` text COLLATE utf8mb4_unicode_ci DEFAULT NULL;
