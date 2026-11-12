-- Tài khoản tạo bằng Google có mật khẩu ngẫu nhiên không ai biết: đánh dấu "chưa đặt mật khẩu" để trang hồ sơ
-- cho phép CÀI ĐẶT mật khẩu (không hỏi mật khẩu hiện tại) thay vì đổi mật khẩu.
ALTER TABLE `users` ADD COLUMN `password_set` BIT(1) NOT NULL DEFAULT b'1';

-- Tài khoản đã có: tạo / kích hoạt qua Google (audit REGISTER / EMAIL_VERIFIED có detail "google")
-- mà sau đó chưa từng đặt lại hoặc đổi mật khẩu thành công.
UPDATE `users` u
SET u.`password_set` = b'0'
WHERE EXISTS (
    SELECT 1 FROM `audit_logs` g
    WHERE g.`actor_id` = u.`id`
      AND g.`action` IN ('REGISTER', 'EMAIL_VERIFIED')
      AND JSON_UNQUOTE(JSON_EXTRACT(g.`metadata`, '$.detail')) = 'google'
      AND NOT EXISTS (
          SELECT 1 FROM `audit_logs` p
          WHERE p.`actor_id` = u.`id`
            AND p.`action` IN ('PASSWORD_RESET_SUCCESS', 'PASSWORD_CHANGED')
            AND p.`created_at` > g.`created_at`
      )
);
