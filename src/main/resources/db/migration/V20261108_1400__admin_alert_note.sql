-- B3 (Toàn): ghi chú của Admin khi xác nhận đã xử lý cảnh báo bảo mật
ALTER TABLE `security_alerts`
    ADD COLUMN `acknowledge_note` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL;
