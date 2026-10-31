-- X2: Bảng người xác nhận chéo DMS (Trusted Social Confirmers) và trạng thái trung gian PENDING_CONFIRMATION

ALTER TABLE dms_configs
    MODIFY COLUMN status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE';

CREATE TABLE IF NOT EXISTS dms_confirmers (
    id BINARY(16) NOT NULL PRIMARY KEY,
    dms_config_id BINARY(16) NOT NULL,
    name VARCHAR(120) NOT NULL,
    email VARCHAR(255) NOT NULL,
    relationship VARCHAR(80) NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    responded_at DATETIME NULL,
    last_token_jti VARCHAR(100) NULL UNIQUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dms_confirmers_config FOREIGN KEY (dms_config_id) REFERENCES dms_configs (id) ON DELETE CASCADE,
    CONSTRAINT uk_dms_confirmer_config_email UNIQUE (dms_config_id, email)
);

