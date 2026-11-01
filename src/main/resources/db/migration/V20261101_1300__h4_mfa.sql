ALTER TABLE users
    ADD COLUMN mfa_enabled BIT(1) NOT NULL DEFAULT b'0',
    ADD COLUMN mfa_secret VARCHAR(512) NULL,
    ADD COLUMN mfa_last_used_step BIGINT NULL,
    ADD COLUMN mfa_challenge_hash VARCHAR(64) NULL,
    ADD COLUMN mfa_challenge_expires_at DATETIME(6) NULL;

CREATE TABLE mfa_backup_codes (
                                  id         BINARY(16)  NOT NULL,
                                  user_id    BINARY(16)  NOT NULL,
                                  code_hash  VARCHAR(64) NOT NULL,
                                  used_at    DATETIME(6) NULL,
                                  created_at DATETIME(6) NOT NULL,
                                  PRIMARY KEY (id),
                                  KEY idx_mfa_backup_user (user_id),
                                  CONSTRAINT fk_mfa_backup_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
