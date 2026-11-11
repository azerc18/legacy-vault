-- Việc 4: mã giảm giá (giảm theo %) và dùng thử VIP một lần cho mỗi tài khoản.
CREATE TABLE `coupons` (
  `id` binary(16) NOT NULL,
  `code` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `percent_off` int NOT NULL,
  `plan_code` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `max_redemptions` int DEFAULT NULL,
  `redeemed_count` int NOT NULL DEFAULT 0,
  `valid_until` datetime(6) DEFAULT NULL,
  `active` tinyint(1) NOT NULL DEFAULT 1,
  `note` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_by` binary(16) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_coupon_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Giao dịch nhớ mã đã dùng và số tiền được giảm; amount_vnd vẫn là số tiền thật phải trả
ALTER TABLE `payment_transactions`
  ADD COLUMN `coupon_code` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD COLUMN `discount_vnd` bigint DEFAULT NULL,
  ADD KEY `idx_payment_user_coupon` (`user_id`, `coupon_code`);

ALTER TABLE `subscriptions`
  ADD COLUMN `trial` tinyint(1) NOT NULL DEFAULT 0;
