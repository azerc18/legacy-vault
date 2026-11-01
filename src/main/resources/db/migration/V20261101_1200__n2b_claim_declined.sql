-- N2b: thêm DECLINED cho trạng thái claim
ALTER TABLE `beneficiary_claims` MODIFY COLUMN `status`
    enum('CLAIMED','DECLINED','EXPIRED','PENDING') COLLATE utf8mb4_unicode_ci NOT NULL;
