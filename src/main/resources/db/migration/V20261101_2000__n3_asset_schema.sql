-- N3: thêm 3 loại tài sản (EXCHANGE_ACCOUNT, E_WALLET, EMAIL) và cột lưu trường công khai theo schema từng loại
ALTER TABLE `digital_assets`
  MODIFY COLUMN `asset_type` enum('BANK_ACCOUNT','CRYPTO_WALLET','DOCUMENT','OTHER','SOCIAL_MEDIA','EXCHANGE_ACCOUNT','E_WALLET','EMAIL')
    COLLATE utf8mb4_unicode_ci NOT NULL,
  ADD COLUMN `public_fields_json` text COLLATE utf8mb4_unicode_ci DEFAULT NULL;

-- N3 (m2): gán cơ chế chuyển giao cho tài sản có từ trước theo loại
UPDATE `digital_assets` SET `transfer_mechanism` = CASE `asset_type`
    WHEN 'BANK_ACCOUNT'  THEN 'LEGAL_CLAIM_DOSSIER'
    WHEN 'DOCUMENT'      THEN 'LEGAL_CLAIM_DOSSIER'
    WHEN 'CRYPTO_WALLET' THEN 'CRYPTOGRAPHIC_SPLIT_KEY'
    WHEN 'SOCIAL_MEDIA'  THEN 'EXECUTOR_DISPOSITION'
    ELSE NULL END
WHERE `transfer_mechanism` IS NULL;
