package com.ltld.app.legacyvault.enums;

public enum NotificationType {
    EXECUTOR_ACTIVATED,
    VAULT_UNLOCKED,
    // FR-11 / FR-12 / FR-14: thông báo trong app cho Executor
    VERIFIER_REPLY,
    VERIFICATION_DECIDED,
    // N2-notify: phần tài sản của người khác đã từ chối được chuyển sang mình (fallback)
    ASSET_REASSIGNED
}
