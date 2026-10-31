-- T1: Bảng gói thuê bao (FREE, VIP_MONTH, VIP_YEAR), đăng ký gói của người dùng và lịch sử giao dịch VNPay

CREATE TABLE IF NOT EXISTS subscription_plans (
    id BINARY(16) NOT NULL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    price_vnd BIGINT NOT NULL DEFAULT 0,
    duration_days INT NULL,
    max_vaults INT NOT NULL DEFAULT 1,
    max_beneficiaries INT NOT NULL DEFAULT 2,
    storage_quota_bytes BIGINT NOT NULL DEFAULT 52428800,
    legacy_messages_enabled BIT(1) NOT NULL DEFAULT b'0',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE TABLE IF NOT EXISTS subscriptions (
    id BINARY(16) NOT NULL PRIMARY KEY,
    user_id BINARY(16) NOT NULL,
    plan_id BINARY(16) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    started_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NULL,
    reminder_sent BIT(1) NOT NULL DEFAULT b'0',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NULL,
    CONSTRAINT fk_subscriptions_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_subscriptions_plan FOREIGN KEY (plan_id) REFERENCES subscription_plans(id),
    INDEX idx_subscription_user_status (user_id, status),
    INDEX idx_subscription_status_expires (status, expires_at)
);

CREATE TABLE IF NOT EXISTS payment_transactions (
    id BINARY(16) NOT NULL PRIMARY KEY,
    user_id BINARY(16) NOT NULL,
    plan_id BINARY(16) NOT NULL,
    amount_vnd BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    vnp_transaction_no VARCHAR(100) NULL,
    vnp_response_code VARCHAR(20) NULL,
    vnp_bank_code VARCHAR(50) NULL,
    paid_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_payment_tx_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_payment_tx_plan FOREIGN KEY (plan_id) REFERENCES subscription_plans(id),
    INDEX idx_payment_tx_user_created (user_id, created_at)
);

-- Mở rộng cột action của bảng audit_logs để hỗ trợ các action mới của T1 (PAYMENT_SUCCEEDED, PAYMENT_FAILED)
ALTER TABLE audit_logs MODIFY COLUMN action VARCHAR(100) NOT NULL;

-- Seed 3 gói chuẩn: FREE (0đ, 1 Master Vault, 2 người thụ hưởng, 50MB), VIP_MONTH (99.000đ, 5 Vaults, 10 người thụ hưởng, 5GB), VIP_YEAR (990.000đ)
INSERT INTO subscription_plans (id, code, name, price_vnd, duration_days, max_vaults, max_beneficiaries, storage_quota_bytes, legacy_messages_enabled, created_at)
VALUES
    (UNHEX(REPLACE(UUID(), '-', '')), 'FREE', 'Free Plan', 0, NULL, 1, 2, 52428800, b'0', CURRENT_TIMESTAMP(6)),
    (UNHEX(REPLACE(UUID(), '-', '')), 'VIP_MONTH', 'VIP Monthly', 99000, 30, 5, 10, 5368709120, b'1', CURRENT_TIMESTAMP(6)),
    (UNHEX(REPLACE(UUID(), '-', '')), 'VIP_YEAR', 'VIP Yearly', 990000, 365, 5, 10, 5368709120, b'1', CURRENT_TIMESTAMP(6))
ON DUPLICATE KEY UPDATE name = VALUES(name);

