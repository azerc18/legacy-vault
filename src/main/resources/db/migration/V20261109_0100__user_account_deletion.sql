-- Trang Hồ sơ: người dùng tự xoá tài khoản (xoá mềm + ẩn danh hoá).
-- Thêm trạng thái DELETED và thời điểm xoá; giữ nguyên dòng users để không gãy khoá ngoại (audit, thanh toán, hoá đơn).
ALTER TABLE users
  MODIFY COLUMN `status` enum('ACTIVE','DELETED','LOCKED','PENDING') COLLATE utf8mb4_unicode_ci NOT NULL,
  ADD COLUMN `deleted_at` datetime(6) DEFAULT NULL;
