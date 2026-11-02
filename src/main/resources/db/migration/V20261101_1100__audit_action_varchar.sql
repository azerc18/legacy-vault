-- H0b: action luu dang chuoi, them gia tri AuditAction moi khong can ALTER ENUM nua
ALTER TABLE `audit_logs` MODIFY COLUMN `action` VARCHAR(100) COLLATE utf8mb4_unicode_ci NOT NULL;