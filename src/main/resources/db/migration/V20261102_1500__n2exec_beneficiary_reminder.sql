-- N2-exec: Executor nhắc người thụ hưởng chưa phản hồi, tối đa 1 lần/24h mỗi người
ALTER TABLE `vault_beneficiaries` ADD COLUMN `last_reminder_at` datetime(6) DEFAULT NULL;
