package com.ltld.app.legacyvault.enums;

/**
 * Trạng thái bàn giao của Vault theo vòng đời bàn giao (FR-10).
 */
public enum HandoverStatus {
    PENDING,
    VERIFIED,
    UNLOCKED,
    CLAIMED,
    ARCHIVED_LOCKED
}
