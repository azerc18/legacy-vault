-- N2a: nhieu nguoi thu huong moi vault + phan bo tai san N:M

CREATE TABLE `vault_beneficiaries` (
  `created_at` datetime(6) NOT NULL,
  `responded_at` datetime(6) DEFAULT NULL,
  `id` binary(16) NOT NULL,
  `user_id` binary(16) DEFAULT NULL,
  `vault_id` binary(16) NOT NULL,
  `relationship` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `email` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `full_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` enum('ACCEPTED','DECLINED','INVITED','PENDING_REGISTRATION','VERIFIED','WAIVED') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_vault_beneficiary_email` (`vault_id`,`email`),
  KEY `idx_vault_beneficiary_email` (`email`),
  KEY `idx_vault_beneficiary_user` (`user_id`),
  CONSTRAINT `fk_vault_beneficiary_vault` FOREIGN KEY (`vault_id`) REFERENCES `vaults` (`id`),
  CONSTRAINT `fk_vault_beneficiary_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `asset_allocations` (
  `share_percent` decimal(5,2) NOT NULL,
  `asset_id` binary(16) NOT NULL,
  `contingent_beneficiary_id` binary(16) DEFAULT NULL,
  `effective_beneficiary_id` binary(16) DEFAULT NULL,
  `id` binary(16) NOT NULL,
  `vault_beneficiary_id` binary(16) NOT NULL,
  `allocation_note` text COLLATE utf8mb4_unicode_ci,
  `allocation_mode` enum('EXCLUSIVE','JOINT_CONSENSUS','PROPORTIONAL') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_allocation_asset_beneficiary` (`asset_id`,`vault_beneficiary_id`),
  KEY `idx_allocation_beneficiary` (`vault_beneficiary_id`),
  KEY `idx_allocation_contingent` (`contingent_beneficiary_id`),
  KEY `idx_allocation_effective` (`effective_beneficiary_id`),
  CONSTRAINT `fk_allocation_asset` FOREIGN KEY (`asset_id`) REFERENCES `digital_assets` (`id`),
  CONSTRAINT `fk_allocation_beneficiary` FOREIGN KEY (`vault_beneficiary_id`) REFERENCES `vault_beneficiaries` (`id`),
  CONSTRAINT `fk_allocation_contingent` FOREIGN KEY (`contingent_beneficiary_id`) REFERENCES `vault_beneficiaries` (`id`),
  CONSTRAINT `fk_allocation_effective` FOREIGN KEY (`effective_beneficiary_id`) REFERENCES `vault_beneficiaries` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `digital_assets`
  ADD COLUMN `transfer_mechanism` enum('CRYPTOGRAPHIC_SPLIT_KEY','EXECUTOR_DISPOSITION','LEGAL_CLAIM_DOSSIER')
      COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD COLUMN `linked_document_group_id` binary(16) DEFAULT NULL;

-- Du lieu cu: nguoi thu huong da co tai khoan (vault CLAIMED coi nhu ACCEPTED)
INSERT INTO `vault_beneficiaries` (`id`, `vault_id`, `user_id`, `email`, `full_name`, `status`, `created_at`)
SELECT UUID_TO_BIN(UUID()), v.`id`, u.`id`, LOWER(u.`email`), u.`full_name`,
       CASE WHEN v.`status` = 'CLAIMED' THEN 'ACCEPTED' ELSE 'INVITED' END,
       NOW(6)
FROM `vaults` v
JOIN `users` u ON u.`id` = v.`beneficiary_id`;

-- Du lieu cu: nguoi duoc moi nhung chua dang ky
INSERT INTO `vault_beneficiaries` (`id`, `vault_id`, `user_id`, `email`, `status`, `created_at`)
SELECT UUID_TO_BIN(UUID()), v.`id`, NULL, LOWER(v.`beneficiary_email`), 'PENDING_REGISTRATION', NOW(6)
FROM `vaults` v
WHERE v.`beneficiary_id` IS NULL AND v.`beneficiary_email` IS NOT NULL;

-- beneficiary_claims: them unique moi TRUOC (vault_id dung dau nen van phuc vu FK), bo unique cu SAU
ALTER TABLE `beneficiary_claims`
  ADD CONSTRAINT `uk_claim_vault_beneficiary` UNIQUE (`vault_id`, `beneficiary_id`);
ALTER TABLE `beneficiary_claims` DROP INDEX `UKlxyh6xnapvbxdddqom87f7jm2`;
