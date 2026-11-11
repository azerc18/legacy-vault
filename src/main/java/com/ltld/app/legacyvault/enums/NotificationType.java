package com.ltld.app.legacyvault.enums;

public enum NotificationType {
    EXECUTOR_ACTIVATED,
    VAULT_UNLOCKED,
    // FR-11 / FR-12 / FR-14: thông báo trong app cho Executor
    VERIFIER_REPLY,
    VERIFICATION_DECIDED,
    // N2-notify: phần tài sản của người khác đã từ chối được chuyển sang mình (fallback)
    ASSET_REASSIGNED,
    // F4b: thông báo có lời nhắn di nguyện mới được mở khóa
    LEGACY_MESSAGE_DELIVERED,
    // PR 2: Beneficiary phản đối kết quả của Executor; Owner cần xác thực lại tài sản
    EXECUTOR_TASK_DISPUTED,
    ASSET_REVERIFY_NEEDED,
    // Chuông thông báo: các sự kiện trước đây chỉ gửi email. Thêm loại mới thì thêm vào ENUM của cột
    // notifications.type (migration) và khóa notifications.types.<TYPE> trong i18n của FE.
    BENEFICIARY_ASSIGNED,
    BENEFICIARY_REMOVED,
    EXECUTOR_ASSIGNED,
    EXECUTOR_REVOKED,
    DMS_CHECKIN_REMINDER,
    DMS_CONFIRMATION_PENDING,
    DMS_CONFIRMATION_REQUESTED,
    DMS_OWNER_CONFIRMED_SAFE,
    DMS_PAUSED,
    DMS_RESUMED,
    ASSET_VERIFIED,
    PAYMENT_SUCCEEDED,
    SUBSCRIPTION_EXPIRING,
    SUBSCRIPTION_EXPIRED,
    CLAIM_DEADLINE_REMINDER,
    EXECUTOR_TASK_COMPLETED,
    VAULT_ARCHIVED,
    VERIFICATION_REOPENED,
    // Việc 3: admin tặng VIP / hoàn tiền
    SUBSCRIPTION_GRANTED,
    PAYMENT_REFUNDED,
    // Lời mời làm Executor: báo Owner khi người được mời đồng ý / từ chối
    EXECUTOR_INVITATION_ACCEPTED,
    EXECUTOR_INVITATION_DECLINED
}
