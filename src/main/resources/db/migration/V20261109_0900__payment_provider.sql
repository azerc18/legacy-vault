-- PayPal: giao dịch cũ đều là VNPay. gateway_txn_ref là order id của PayPal, amount_usd_cents là số USD đã tính lúc checkout.
ALTER TABLE payment_transactions
    ADD COLUMN provider VARCHAR(20) NOT NULL DEFAULT 'VNPAY',
    ADD COLUMN gateway_txn_ref VARCHAR(100) NULL,
    ADD COLUMN amount_usd_cents BIGINT NULL,
    ADD CONSTRAINT uk_payment_tx_gateway_ref UNIQUE (gateway_txn_ref);
