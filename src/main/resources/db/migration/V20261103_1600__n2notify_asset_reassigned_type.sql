-- N2-notify: thêm loại thông báo ASSET_REASSIGNED (phần tài sản được chuyển sang người nhận thay)
ALTER TABLE notifications
    MODIFY COLUMN `type` ENUM('EXECUTOR_ACTIVATED','VAULT_UNLOCKED','VERIFICATION_DECIDED','VERIFIER_REPLY','ASSET_REASSIGNED')
    COLLATE utf8mb4_unicode_ci NOT NULL;