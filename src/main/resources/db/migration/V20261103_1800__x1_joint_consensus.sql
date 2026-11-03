-- X1: đồng thuận K-of-N (JOINT_CONSENSUS) bằng Shamir. Mỗi dòng phân bổ giữ 1 phần khóa đã mã hóa.
ALTER TABLE `asset_allocations`
    ADD COLUMN `consensus_threshold` int DEFAULT NULL,
    ADD COLUMN `consensus_share` text COLLATE utf8mb4_unicode_ci,
    ADD COLUMN `consensus_key_hash` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
    ADD COLUMN `consensus_approved_at` datetime(6) DEFAULT NULL;
