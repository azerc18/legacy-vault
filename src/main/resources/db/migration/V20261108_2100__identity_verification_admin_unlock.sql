-- #10: Admin mở khóa sớm xác minh danh tính của người thụ hưởng (phiên FAILED có admin_unlocked_at thì hết khóa)
ALTER TABLE `identity_verifications` ADD COLUMN `admin_unlocked_at` datetime(6) DEFAULT NULL;
