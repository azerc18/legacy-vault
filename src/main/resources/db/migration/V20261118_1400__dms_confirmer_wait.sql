-- Trang "Chính sách hệ thống": Admin chỉnh thời gian chờ người xác nhận chéo (Trusted Confirmer) trả lời trước khi
-- DMS tự kích hoạt. Trước đây viết cứng 72 giờ (DmsConfirmerServiceImpl.CONFIRMATION_TIMEOUT_HOURS); mặc định giữ 72.
-- Chỉ áp dụng cho đợt chờ xác nhận BẮT ĐẦU sau khi lưu; đợt đang chờ giữ hạn đã chốt trong dms_configs.next_checkin_due_at.
-- Version phải lớn hơn mọi migration trên main, kể cả V20261118_1300 / _1310 của PR Tích hợp.
ALTER TABLE `dms_policy_settings`
  ADD COLUMN `confirmer_wait_hours` smallint NOT NULL DEFAULT 72 AFTER `backup_activation_wait_days`;
