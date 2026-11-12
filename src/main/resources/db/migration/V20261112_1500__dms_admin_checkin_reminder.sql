-- Admin chủ động nhắc Owner check-in DMS: lưu lần nhắc gần nhất để chặn bấm liên tục.
ALTER TABLE `dms_configs`
  ADD COLUMN `last_admin_reminder_at` DATETIME(6) NULL;
