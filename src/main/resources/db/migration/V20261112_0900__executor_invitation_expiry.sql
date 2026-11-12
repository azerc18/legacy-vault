-- Hạn trả lời lời mời làm Executor: quá hạn thì hệ thống tự thu hồi để vị trí không treo mãi ở PENDING.
-- EXPIRED đi kèm ExecutorStatus.REVOKED, giống DECLINED.
ALTER TABLE `executor_assignments`
  MODIFY COLUMN `response_status` ENUM('PENDING','ACCEPTED','DECLINED','EXPIRED') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACCEPTED',
  ADD COLUMN `invitation_expires_at` DATETIME(6) NULL,
  ADD COLUMN `invitation_reminder_sent_at` DATETIME(6) NULL;

-- Lời mời đang chờ của dữ liệu cũ: đặt hạn 7 ngày kể từ lúc được chỉ định (app.executor.invitation-expiry-days).
-- Dòng nào đã quá hạn thì job quét sẽ thu hồi ở lần chạy đầu tiên.
UPDATE `executor_assignments`
SET `invitation_expires_at` = DATE_ADD(COALESCE(`assigned_at`, `created_at`), INTERVAL 7 DAY)
WHERE `response_status` = 'PENDING' AND `status` <> 'REVOKED' AND `invitation_expires_at` IS NULL;

-- Thông báo mới trên chuông cho Owner. GIỮ mọi loại đã có (xem V20261111_1300).
ALTER TABLE `notifications`
  MODIFY COLUMN `type` ENUM('EXECUTOR_ACTIVATED','VAULT_UNLOCKED','VERIFICATION_DECIDED','VERIFIER_REPLY','ASSET_REASSIGNED','LEGACY_MESSAGE_DELIVERED','EXECUTOR_TASK_DISPUTED','ASSET_REVERIFY_NEEDED',
    'BENEFICIARY_ASSIGNED','BENEFICIARY_REMOVED','EXECUTOR_ASSIGNED','EXECUTOR_REVOKED',
    'DMS_CHECKIN_REMINDER','DMS_CONFIRMATION_PENDING','DMS_CONFIRMATION_REQUESTED','DMS_OWNER_CONFIRMED_SAFE','DMS_PAUSED','DMS_RESUMED',
    'ASSET_VERIFIED','PAYMENT_SUCCEEDED','SUBSCRIPTION_EXPIRING','SUBSCRIPTION_EXPIRED',
    'CLAIM_DEADLINE_REMINDER','EXECUTOR_TASK_COMPLETED','VAULT_ARCHIVED','VERIFICATION_REOPENED',
    'SUBSCRIPTION_GRANTED','PAYMENT_REFUNDED',
    'EXECUTOR_INVITATION_ACCEPTED','EXECUTOR_INVITATION_DECLINED','EXECUTOR_INVITATION_EXPIRED')
  COLLATE utf8mb4_unicode_ci NOT NULL;
