-- Việc 6: Owner hủy gia hạn cuối kỳ. Gói vẫn ACTIVE tới expires_at rồi job hết hạn đưa về FREE như thường.
ALTER TABLE `subscriptions`
  ADD COLUMN `cancel_at_period_end` BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN `cancelled_at` DATETIME(6) NULL;
