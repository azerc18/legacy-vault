-- #19: các truy vấn FR-17 (findPendingSession, assertNotLocked, assertViewSession, sumAttemptsOfOtherPendingSessions)
-- đều lọc theo (vault_id, beneficiary_id, status) rồi theo thời gian. Chỉ có index FK đơn lẻ nên thêm index ghép.
CREATE INDEX `idx_identity_verification_lookup`
    ON `identity_verifications` (`vault_id`, `beneficiary_id`, `status`, `created_at`);
