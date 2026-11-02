-- X5: Quản lý phiên bản tài liệu pháp lý (Di chúc v1, v2, v3...) theo Điều 640 BLDS 2015
ALTER TABLE `legal_documents`
  ADD COLUMN `document_group_id` binary(16) DEFAULT NULL AFTER `id`,
  ADD COLUMN `version` int NOT NULL DEFAULT 1 AFTER `file_url_encrypted`,
  ADD COLUMN `is_current` bit(1) NOT NULL DEFAULT b'1' AFTER `version`;

-- Backfill dữ liệu cũ: mỗi tài liệu đã có thành bản version 1 của chính nó
UPDATE `legal_documents` SET `document_group_id` = `id` WHERE `document_group_id` IS NULL;

ALTER TABLE `legal_documents`
  MODIFY COLUMN `document_group_id` binary(16) NOT NULL;

CREATE INDEX `idx_legal_doc_group` ON `legal_documents` (`document_group_id`);
CREATE INDEX `idx_legal_doc_vault_current` ON `legal_documents` (`vault_id`, `is_current`);
CREATE UNIQUE INDEX `uk_legal_doc_group_version` ON `legal_documents` (`document_group_id`, `version`);

