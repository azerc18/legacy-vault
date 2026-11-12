-- Người xác nhận chéo báo "Owner vẫn an toàn" = một lần check-in thay Owner (bắt đầu chu kỳ mới đầy đủ).
-- Lưu thời điểm và tên người xác nhận để trang DMS của Owner hiển thị; Owner tự check-in thì xoá.
ALTER TABLE `dms_configs`
  ADD COLUMN `vouched_at` datetime(6) DEFAULT NULL,
  ADD COLUMN `vouched_by_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL;
