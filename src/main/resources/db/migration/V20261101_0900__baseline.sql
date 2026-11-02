SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE `asset_access_records` (
  `first_accessed_at` datetime(6) NOT NULL,
  `asset_id` binary(16) NOT NULL,
  `claim_id` binary(16) NOT NULL,
  `id` binary(16) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_access_claim_asset` (`claim_id`,`asset_id`),
  KEY `FKa3votwxp6y28sdal8p4lv4hs4` (`asset_id`),
  CONSTRAINT `FK9shyovyc48gsiymrf85nl0fq2` FOREIGN KEY (`claim_id`) REFERENCES `beneficiary_claims` (`id`),
  CONSTRAINT `FKa3votwxp6y28sdal8p4lv4hs4` FOREIGN KEY (`asset_id`) REFERENCES `digital_assets` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `audit_logs` (
  `created_at` datetime(6) NOT NULL,
  `actor_id` binary(16) DEFAULT NULL,
  `entity_id` binary(16) DEFAULT NULL,
  `id` binary(16) NOT NULL,
  `vault_id` binary(16) DEFAULT NULL,
  `ip_address` varchar(45) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `entity_type` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `action` enum('ACCOUNT_LOCKED','ADMIN_VAULT_VIEWED','ASSET_CREATED','ASSET_DELETED','ASSET_DOWNLOADED','ASSET_UPDATED','ASSET_VIEWED','AVATAR_DELETED','AVATAR_UPDATED','BENEFICIARY_ASSIGNED','BENEFICIARY_ASSIGN_DENIED','BENEFICIARY_LINKED','BENEFICIARY_NOTIFIED','BENEFICIARY_NOTIFY_FAILED','BENEFICIARY_NOTIFY_GAVE_UP','BENEFICIARY_PENDING_NOTIFIED','BENEFICIARY_REMOVED','BENEFICIARY_REMOVE_DENIED','BENEFICIARY_REPLACED','CLAIM_COMPLETED','CLAIM_INITIATED','DMS_CHECKIN','DMS_CONFIGURED','DMS_RESUMED','DMS_SUSPENDED','DMS_TRIGGERED','DMS_TRIGGERED_NO_EXECUTOR','DMS_TRIGGER_DEFERRED','DOCUMENT_DELETED','DOCUMENT_DOWNLOADED','DOCUMENT_UPLOADED','EMAIL_VERIFIED','EXECUTOR_ACKNOWLEDGED','EXECUTOR_ACTIVATED','EXECUTOR_ASSIGNED','EXECUTOR_ASSIGNED_BY_ADMIN','EXECUTOR_REPLACED','EXECUTOR_REVOKE','IDENTITY_LOCKED','IDENTITY_OTP_SENT','IDENTITY_VERIFIED','IDENTITY_VERIFY_FAILED','LEGAL_DOCUMENT_VIEWED','LEGAL_REQUEST_APPROVED','LEGAL_REQUEST_REJECTED','LEGAL_REQUEST_REOPENED','LEGAL_REQUEST_SIGNED','LEGAL_SIGN_FAILED','LEGAL_SIGN_OTP_SENT','LEGAL_VERIFICATION_REQUESTED','LOGIN','LOGIN_FAILED','LOGOUT','OTP_FAILED','OTP_RATE_LIMITED','OTP_SENT','PASSWORD_CHANGED','PASSWORD_CHANGE_FAILED','PASSWORD_RESET_FAILED','PASSWORD_RESET_REQUEST','PASSWORD_RESET_SUCCESS','PROFILE_UPDATED','RBAC_CHANGE','REGISTER','SECURITY_ALERT_ACKNOWLEDGED','TOKEN_REFRESH','TOKEN_REFRESH_FAILED','TOKEN_REUSE_DETECTED','VAULT_ARCHIVED','VAULT_CREATED','VAULT_DELETED','VAULT_DELETE_DENIED','VAULT_PURGED','VAULT_UNLOCK','VAULT_UPDATED','VERIFICATION_ATTACHMENT_VIEWED','VERIFICATION_MESSAGE_SENT') COLLATE utf8mb4_unicode_ci NOT NULL,
  `metadata` json DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_audit_actor` (`actor_id`),
  KEY `idx_audit_action` (`action`),
  KEY `idx_audit_vault_created` (`vault_id`,`created_at`),
  KEY `idx_audit_entity` (`entity_type`,`entity_id`),
  KEY `idx_audit_created` (`created_at`),
  CONSTRAINT `FKf2uqqjmo7gvd8fq7ac81fgc0m` FOREIGN KEY (`actor_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `authorities` (
  `id` binary(16) NOT NULL,
  `code` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKo3dg4dkhlj82n0iv1i09ubtpt` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `beneficiary_claims` (
  `notify_attempts` int NOT NULL DEFAULT '0',
  `claim_deadline_at` datetime(6) NOT NULL,
  `claimed_at` datetime(6) DEFAULT NULL,
  `last_notify_attempt_at` datetime(6) DEFAULT NULL,
  `notified_at` datetime(6) DEFAULT NULL,
  `beneficiary_id` binary(16) NOT NULL,
  `id` binary(16) NOT NULL,
  `vault_id` binary(16) NOT NULL,
  `status` enum('CLAIMED','EXPIRED','PENDING') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKlxyh6xnapvbxdddqom87f7jm2` (`vault_id`),
  KEY `FKgtxvni12gr3o1vfhe2ypvhx18` (`beneficiary_id`),
  CONSTRAINT `FK6a977b79t0bviayovyrq9rsgh` FOREIGN KEY (`vault_id`) REFERENCES `vaults` (`id`),
  CONSTRAINT `FKgtxvni12gr3o1vfhe2ypvhx18` FOREIGN KEY (`beneficiary_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `digital_assets` (
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `id` binary(16) NOT NULL,
  `vault_id` binary(16) NOT NULL,
  `attachment_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `asset_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `encrypted_secret` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `encryption_key_ref` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `notes_encrypted` text COLLATE utf8mb4_unicode_ci,
  `asset_type` enum('BANK_ACCOUNT','CRYPTO_WALLET','DOCUMENT','OTHER','SOCIAL_MEDIA') COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('ACTIVE','REVOKED') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKqrgywqsmomjrhk5p3ru9hkgei` (`vault_id`),
  CONSTRAINT `FKqrgywqsmomjrhk5p3ru9hkgei` FOREIGN KEY (`vault_id`) REFERENCES `vaults` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `digital_signatures` (
  `signed_at` datetime(6) NOT NULL,
  `id` binary(16) NOT NULL,
  `request_id` binary(16) NOT NULL,
  `verifier_id` binary(16) NOT NULL,
  `signature_hash` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `signature_method` enum('MOCK_OTP','MOCK_PIN') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKibjlfxfhchgjq7tsvqsqasso4` (`request_id`),
  KEY `FKh4e7rxi3g3jg32c2f30dlf70m` (`verifier_id`),
  CONSTRAINT `FKh4e7rxi3g3jg32c2f30dlf70m` FOREIGN KEY (`verifier_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKhdm2ub4qyaos3f0wttasjcvo2` FOREIGN KEY (`request_id`) REFERENCES `legal_verification_requests` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `dms_checkins` (
  `occurred_at` datetime(6) NOT NULL,
  `dms_config_id` binary(16) NOT NULL,
  `id` binary(16) NOT NULL,
  `channel` enum('EMAIL','IN_APP') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `checkin_type` enum('OWNER_RESPONDED','REMINDER_SENT','TIMEOUT') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKcvt2gvjh3alh4uolbjl8kqeir` (`dms_config_id`),
  CONSTRAINT `FKcvt2gvjh3alh4uolbjl8kqeir` FOREIGN KEY (`dms_config_id`) REFERENCES `dms_configs` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `dms_configs` (
  `backup_activation_wait_days` int NOT NULL,
  `checkin_interval_days` int NOT NULL,
  `grace_period_days` int DEFAULT NULL,
  `reminder_count` int DEFAULT NULL,
  `reminders_sent` int NOT NULL DEFAULT '0',
  `trigger_deferrals` int NOT NULL DEFAULT '0',
  `created_at` datetime(6) NOT NULL,
  `last_checkin_at` datetime(6) DEFAULT NULL,
  `next_checkin_due_at` datetime(6) DEFAULT NULL,
  `next_reminder_at` datetime(6) DEFAULT NULL,
  `triggered_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  `version` bigint NOT NULL,
  `id` binary(16) NOT NULL,
  `owner_id` binary(16) NOT NULL,
  `notification_channel` enum('EMAIL','IN_APP') COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('ACTIVE','SUSPENDED','TRIGGERED') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dms_config_owner` (`owner_id`),
  KEY `idx_dms_config_status_next_reminder` (`status`,`next_reminder_at`),
  CONSTRAINT `FK4h5lni05ux4letuk0kqdxiom3` FOREIGN KEY (`owner_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `executor_assignments` (
  `acknowledged_at` datetime(6) DEFAULT NULL,
  `activated_at` datetime(6) DEFAULT NULL,
  `assigned_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `revoked_at` datetime(6) DEFAULT NULL,
  `version` bigint NOT NULL,
  `executor_user_id` binary(16) NOT NULL,
  `id` binary(16) NOT NULL,
  `owner_id` binary(16) NOT NULL,
  `executor_type` enum('BACKUP','PRIMARY') COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('ACTIVE','INACTIVE','REVOKED') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_executor_assignment_owner_type` (`owner_id`,`executor_type`),
  KEY `FKbcm1ju1yns5qpocwg78wlkb2b` (`executor_user_id`),
  CONSTRAINT `FKbcm1ju1yns5qpocwg78wlkb2b` FOREIGN KEY (`executor_user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKclmp69trxd7en6t542fv4bvf9` FOREIGN KEY (`owner_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `identity_verifications` (
  `attempt_count` int DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `otp_expires_at` datetime(6) DEFAULT NULL,
  `otp_sent_at` datetime(6) DEFAULT NULL,
  `verified_at` datetime(6) DEFAULT NULL,
  `beneficiary_id` binary(16) NOT NULL,
  `id` binary(16) NOT NULL,
  `vault_id` binary(16) NOT NULL,
  `otp_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `method` enum('EKYC_MOCK','OTP') COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('FAILED','PENDING','SUCCESS') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKstro5dpckg6y6kq8vo7eifl1l` (`beneficiary_id`),
  KEY `FKs0ihyh9n86peh1b35bs3twtxm` (`vault_id`),
  CONSTRAINT `FKs0ihyh9n86peh1b35bs3twtxm` FOREIGN KEY (`vault_id`) REFERENCES `vaults` (`id`),
  CONSTRAINT `FKstro5dpckg6y6kq8vo7eifl1l` FOREIGN KEY (`beneficiary_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `legal_documents` (
  `uploaded_at` datetime(6) NOT NULL,
  `id` binary(16) NOT NULL,
  `vault_id` binary(16) DEFAULT NULL,
  `file_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `file_url_encrypted` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `document_type` enum('IDENTITY_CARD','OTHER','OWNERSHIP_PAPER','WILL') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK59f845d0b3f2lftkbbqlg6lx5` (`vault_id`),
  CONSTRAINT `FK59f845d0b3f2lftkbbqlg6lx5` FOREIGN KEY (`vault_id`) REFERENCES `vaults` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `legal_verification_requests` (
  `sign_attempts` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `decided_at` datetime(6) DEFAULT NULL,
  `executor_last_read_at` datetime(6) DEFAULT NULL,
  `sign_otp_expires_at` datetime(6) DEFAULT NULL,
  `sign_otp_sent_at` datetime(6) DEFAULT NULL,
  `version` bigint NOT NULL,
  `executor_id` binary(16) NOT NULL,
  `id` binary(16) NOT NULL,
  `owner_id` binary(16) NOT NULL,
  `verifier_id` binary(16) DEFAULT NULL,
  `sign_otp_hash` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `death_certificate_file_url_encrypted` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `notes` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `rejection_reason` text COLLATE utf8mb4_unicode_ci,
  `rejection_reason_code` enum('DOCUMENT_INCOMPLETE','DOCUMENT_INVALID','DOCUMENT_UNREADABLE','INFO_MISMATCH','OTHER','SUSPECTED_FORGERY') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` enum('APPROVED','PENDING','REJECTED','SIGNED') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKhfftk71nl0urhh6wvksofvxhg` (`executor_id`),
  KEY `FKpnwqral9a6xbg1c0gikh1pae1` (`owner_id`),
  KEY `FKr66xxogu9pjae50xevnh4043r` (`verifier_id`),
  CONSTRAINT `FKhfftk71nl0urhh6wvksofvxhg` FOREIGN KEY (`executor_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKpnwqral9a6xbg1c0gikh1pae1` FOREIGN KEY (`owner_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKr66xxogu9pjae50xevnh4043r` FOREIGN KEY (`verifier_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `notifications` (
  `attempt_count` int NOT NULL DEFAULT '0',
  `created_at` datetime(6) NOT NULL,
  `last_attempt_at` datetime(6) DEFAULT NULL,
  `sent_at` datetime(6) DEFAULT NULL,
  `id` binary(16) NOT NULL,
  `recipient_id` binary(16) NOT NULL,
  `reference_id` binary(16) DEFAULT NULL,
  `reference_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `content` text COLLATE utf8mb4_unicode_ci,
  `channel` enum('EMAIL','IN_APP') COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('FAILED','QUEUED','READ','SENT') COLLATE utf8mb4_unicode_ci NOT NULL,
  `type` enum('EXECUTOR_ACTIVATED','VAULT_UNLOCKED','VERIFICATION_DECIDED','VERIFIER_REPLY') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKqqnsjxlwleyjbxlmm213jaj3f` (`recipient_id`),
  CONSTRAINT `FKqqnsjxlwleyjbxlmm213jaj3f` FOREIGN KEY (`recipient_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `refresh_tokens` (
  `created_at` datetime(6) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `issued_at` datetime(6) NOT NULL,
  `revoked_at` datetime(6) DEFAULT NULL,
  `version` bigint NOT NULL,
  `id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `ip_address` varchar(45) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `token_hash` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_agent` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `revoked_reason` enum('ADMIN_REVOKE','EXPIRED','LOGOUT','PASSWORD_CHANGED','REUSE_DETECTED','ROTATED') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKo2mlirhldriil2y7krapq4frt` (`token_hash`),
  KEY `FK1lih5y2npsf8u5o3vhdb9y0os` (`user_id`),
  CONSTRAINT `FK1lih5y2npsf8u5o3vhdb9y0os` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `role_authorities` (
  `authority_id` binary(16) NOT NULL,
  `role_id` binary(16) NOT NULL,
  PRIMARY KEY (`authority_id`,`role_id`),
  KEY `FKffl6vh23qrnrld2sxfhtjhbkm` (`role_id`),
  CONSTRAINT `FKffl6vh23qrnrld2sxfhtjhbkm` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`),
  CONSTRAINT `FKpmbd5d32ddnedap7prbfhwuy2` FOREIGN KEY (`authority_id`) REFERENCES `authorities` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `roles` (
  `id` binary(16) NOT NULL,
  `code` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKch1113horj4qr56f91omojv8` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `security_alerts` (
  `event_count` int NOT NULL,
  `acknowledged_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `first_seen_at` datetime(6) NOT NULL,
  `last_seen_at` datetime(6) NOT NULL,
  `version` bigint NOT NULL,
  `acknowledged_by` binary(16) DEFAULT NULL,
  `id` binary(16) NOT NULL,
  `subject` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `message` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `rule` enum('ACCOUNT_LOCKED','ADMIN_ROLE_GRANTED','LOGIN_FAILURES_FROM_IP','MASS_DOWNLOAD','REFRESH_TOKEN_REUSE') COLLATE utf8mb4_unicode_ci NOT NULL,
  `severity` enum('HIGH','MEDIUM') COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('ACKNOWLEDGED','OPEN') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_alert_status_created` (`status`,`created_at`),
  KEY `idx_alert_rule_subject` (`rule`,`subject`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `user_roles` (
  `role_id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  PRIMARY KEY (`role_id`,`user_id`),
  KEY `FKhfh9dx7w3ubf1co1vdev94g3f` (`user_id`),
  CONSTRAINT `FKh8ciramu9cc9q3qcqiv4ue8a6` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`),
  CONSTRAINT `FKhfh9dx7w3ubf1co1vdev94g3f` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `users` (
  `failed_login_attempts` int DEFAULT NULL,
  `avatar_updated_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `last_login_at` datetime(6) DEFAULT NULL,
  `locked_until` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  `id` binary(16) NOT NULL,
  `avatar_path` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `email` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `full_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `password_hash` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `phone_number` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `kyc_level` enum('BASIC','NONE','VERIFIED') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` enum('ACTIVE','LOCKED','PENDING') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK6dotkott2kjsp8vw4d0m25fb7` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `vaults` (
  `archived_at` datetime(6) DEFAULT NULL,
  `claim_deadline_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `purge_eligible_at` datetime(6) DEFAULT NULL,
  `unlocked_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  `beneficiary_id` binary(16) DEFAULT NULL,
  `id` binary(16) NOT NULL,
  `owner_id` binary(16) NOT NULL,
  `beneficiary_email` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('ACTIVE','ARCHIVED_LOCKED','CLAIMED','UNLOCKED') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKr2phltf1917vdca762oely2m2` (`beneficiary_id`),
  KEY `FKdhflxh2jyud4l3qswg9qi5agl` (`owner_id`),
  CONSTRAINT `FKdhflxh2jyud4l3qswg9qi5agl` FOREIGN KEY (`owner_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKr2phltf1917vdca762oely2m2` FOREIGN KEY (`beneficiary_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `verification_exchange_messages` (
  `attachment_size` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` binary(16) NOT NULL,
  `request_id` binary(16) NOT NULL,
  `sender_id` binary(16) NOT NULL,
  `attachment_path` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `attachment_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `content` text COLLATE utf8mb4_unicode_ci,
  `sender_role` enum('EXECUTOR','VERIFIER') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_exchange_request_created` (`request_id`,`created_at`),
  KEY `FKkhgmmw91rsi317sb4mp081a6w` (`sender_id`),
  CONSTRAINT `FKkhgmmw91rsi317sb4mp081a6w` FOREIGN KEY (`sender_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKlsv79wkffucxpfucsbnsmraxk` FOREIGN KEY (`request_id`) REFERENCES `legal_verification_requests` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `verification_request_vaults` (
  `id` binary(16) NOT NULL,
  `request_id` binary(16) NOT NULL,
  `vault_id` binary(16) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKfgptujy9hhr56x4euc7n4js3r` (`request_id`,`vault_id`),
  KEY `FK4deq7o7s1my0naak4gjap2iyj` (`vault_id`),
  CONSTRAINT `FK4deq7o7s1my0naak4gjap2iyj` FOREIGN KEY (`vault_id`) REFERENCES `vaults` (`id`),
  CONSTRAINT `FKhydx1hfcje5fiwg65o85n40xr` FOREIGN KEY (`request_id`) REFERENCES `legal_verification_requests` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `verification_review_progress` (
  `document_valid` bit(1) DEFAULT NULL,
  `checked_at` datetime(6) DEFAULT NULL,
  `viewed_at` datetime(6) DEFAULT NULL,
  `id` binary(16) NOT NULL,
  `request_id` binary(16) NOT NULL,
  `verifier_id` binary(16) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKjxk5xwv1vgbil6ars8a197aew` (`request_id`,`verifier_id`),
  CONSTRAINT `FKt3bbxpkhcgbmgbonu4lv8tp04` FOREIGN KEY (`request_id`) REFERENCES `legal_verification_requests` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `verification_tokens` (
  `attempt_count` int NOT NULL,
  `is_used` bit(1) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `used_at` datetime(6) DEFAULT NULL,
  `version` bigint NOT NULL,
  `id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `otp_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `token_type` enum('EMAIL_VERIFICATION','PASSWORD_RESET') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK54y8mqsnq1rtyf581sfmrbp4f` (`user_id`),
  CONSTRAINT `FK54y8mqsnq1rtyf581sfmrbp4f` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;

