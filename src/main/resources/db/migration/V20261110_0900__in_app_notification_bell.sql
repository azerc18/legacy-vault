-- Chuông thông báo: các sự kiện trước đây chỉ gửi email nay có thêm thông báo trong app.
-- 1. Thêm loại thông báo mới. Nếu main đã thêm loại khác thì GIỮ các loại đó trong danh sách ENUM.
ALTER TABLE `notifications`
  MODIFY COLUMN `type` ENUM('EXECUTOR_ACTIVATED','VAULT_UNLOCKED','VERIFICATION_DECIDED','VERIFIER_REPLY','ASSET_REASSIGNED','LEGACY_MESSAGE_DELIVERED','EXECUTOR_TASK_DISPUTED','ASSET_REVERIFY_NEEDED',
    'BENEFICIARY_ASSIGNED','BENEFICIARY_REMOVED','EXECUTOR_ASSIGNED','EXECUTOR_REVOKED',
    'DMS_CHECKIN_REMINDER','DMS_CONFIRMATION_PENDING','DMS_CONFIRMATION_REQUESTED','DMS_OWNER_CONFIRMED_SAFE','DMS_PAUSED','DMS_RESUMED',
    'ASSET_VERIFIED','PAYMENT_SUCCEEDED','SUBSCRIPTION_EXPIRING','SUBSCRIPTION_EXPIRED',
    'CLAIM_DEADLINE_REMINDER','EXECUTOR_TASK_COMPLETED','VAULT_ARCHIVED','VERIFICATION_REOPENED')
  COLLATE utf8mb4_unicode_ci NOT NULL;

-- 2. Tham số của câu thông báo (JSON phẳng) để FE dịch theo ngôn ngữ đang chọn; null với thông báo cũ.
ALTER TABLE `notifications`
  ADD COLUMN `params` TEXT COLLATE utf8mb4_unicode_ci NULL AFTER `content`;

-- 3. Chuông hỏi số chưa đọc định kỳ và liệt kê theo người nhận, mới nhất trước.
CREATE INDEX `idx_notifications_recipient_channel_created`
  ON `notifications` (`recipient_id`, `channel`, `created_at`);

-- 4. Xóa tài khoản thì xóa luôn thông báo của người đó (trước đây FK chặn việc xóa User đang có thông báo).
--    Tên FK do Hibernate sinh có thể khác nhau giữa các môi trường nên tra trong information_schema rồi mới drop.
SET @fk := (SELECT CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notifications'
              AND COLUMN_NAME = 'recipient_id' AND REFERENCED_TABLE_NAME = 'users' LIMIT 1);
SET @sql := IF(@fk IS NULL, 'SELECT 1', CONCAT('ALTER TABLE `notifications` DROP FOREIGN KEY `', @fk, '`'));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE `notifications`
  ADD CONSTRAINT `fk_notifications_recipient` FOREIGN KEY (`recipient_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;
