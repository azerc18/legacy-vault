-- Trang "Chính sách hệ thống" của Admin: mặc định và giới hạn cấu hình Dead Man's Switch.
-- Chỉ có 1 dòng (id = 1). Trước đây các số này viết cứng: 30 / 7 / 3 ngày-lần ở FE, 1–365 / 1–90 / 1–10 ở
-- DmsConfigRequest, 14 ngày chờ Executor dự phòng ở DmsConfigServiceImpl / ExecutorInvitationServiceImpl.
-- Giới hạn cứng của DmsConfigRequest vẫn giữ: Admin chỉ thu hẹp được trong khoảng đó, không nới rộng ra ngoài.
CREATE TABLE `dms_policy_settings` (
  `id` tinyint NOT NULL,
  `checkin_interval_min_days` int NOT NULL DEFAULT 1,
  `checkin_interval_default_days` int NOT NULL DEFAULT 30,
  `checkin_interval_max_days` int NOT NULL DEFAULT 365,
  `grace_period_min_days` int NOT NULL DEFAULT 1,
  `grace_period_default_days` int NOT NULL DEFAULT 7,
  `grace_period_max_days` int NOT NULL DEFAULT 90,
  `reminder_count_min` int NOT NULL DEFAULT 1,
  `reminder_count_default` int NOT NULL DEFAULT 3,
  `reminder_count_max` int NOT NULL DEFAULT 10,
  `backup_activation_wait_days` int NOT NULL DEFAULT 14,
  `updated_by` binary(16) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `dms_policy_settings` (`id`) VALUES (1);
