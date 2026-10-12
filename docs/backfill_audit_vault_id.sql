-- =============================================================================
-- M4: Backfill vault_id cho các bản ghi audit_logs cũ
-- =============================================================================
-- Khi cột vault_id được thêm vào bảng audit_logs, các log CŨ (trước PR này)
-- có vault_id = NULL. Script này điền lại vault_id từ entity_id khi
-- entity_type = 'Vault'.
--
-- Chạy thủ công MỘT LẦN trên DB sau khi deploy PR này.
-- Kiểm tra trước: SELECT COUNT(*) FROM audit_logs WHERE vault_id IS NULL AND entity_type = 'Vault';
-- =============================================================================

-- Bước 1: Điền vault_id cho các sự kiện trực tiếp trên Vault
--         (VAULT_CREATED, VAULT_DELETED, VAULT_DELETE_DENIED, ...)
UPDATE audit_logs
SET vault_id = entity_id
WHERE vault_id IS NULL
  AND entity_type = 'Vault'
  AND entity_id IS NOT NULL;

-- Bước 2: Điền vault_id cho các sự kiện tài liệu (DOCUMENT_UPLOADED, ...)
--         Các log này có entity_type = 'Document', cần JOIN sang bảng legal_documents
UPDATE audit_logs al
JOIN legal_documents ld ON ld.id = al.entity_id
SET al.vault_id = ld.vault_id
WHERE al.vault_id IS NULL
  AND al.entity_type = 'Document'
  AND al.entity_id IS NOT NULL;

-- Kiểm tra sau khi chạy — nên về 0 (hoặc chỉ còn log hệ thống không gắn vault)
SELECT COUNT(*)
FROM audit_logs
WHERE vault_id IS NULL
  AND entity_type IN ('Vault', 'Document');
