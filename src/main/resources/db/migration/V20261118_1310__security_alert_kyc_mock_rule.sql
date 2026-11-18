-- Trang Tích hợp (#298, review M1): bật eKYC mô phỏng từ trang Admin tạo cảnh báo HIGH KYC_MOCK_ENABLED (mail mọi Admin).
-- Cột rule là enum MySQL nên phải thêm giá trị mới, nếu không lúc lưu cảnh báo sẽ lỗi "Data truncated".
ALTER TABLE `security_alerts`
  MODIFY COLUMN `rule` enum('ACCOUNT_LOCKED','ADMIN_ROLE_GRANTED','KYC_MOCK_ENABLED','LOGIN_FAILURES_FROM_IP','MASS_DOWNLOAD','REFRESH_TOKEN_REUSE') COLLATE utf8mb4_unicode_ci NOT NULL;
