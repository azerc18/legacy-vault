package com.ltld.app.legacyvault.enums;

public enum AuditAction {
    REGISTER, OTP_SENT, OTP_FAILED, EMAIL_VERIFIED,
    LOGIN, LOGIN_FAILED, ACCOUNT_LOCKED, LOGOUT,
    TOKEN_REFRESH, TOKEN_REFRESH_FAILED,
    VAULT_CREATED, VAULT_DELETED, VAULT_DELETE_DENIED, VAULT_UNLOCK, DOCUMENT_UPLOADED,
    CLAIM_INITIATED, EXECUTOR_REVOKE, RBAC_CHANGE, TOKEN_REUSE_DETECTED, OTP_RATE_LIMITED,
    PASSWORD_RESET_REQUEST, PASSWORD_RESET_SUCCESS, PASSWORD_RESET_FAILED,
    IDENTITY_OTP_SENT, IDENTITY_VERIFIED, IDENTITY_VERIFY_FAILED, IDENTITY_LOCKED,
    ASSET_VIEWED, ASSET_DOWNLOADED, CLAIM_COMPLETED,
    // FR-12/14: audit quyết định pháp lý
    LEGAL_REQUEST_APPROVED, LEGAL_REQUEST_REJECTED, LEGAL_REQUEST_SIGNED, LEGAL_SIGN_FAILED,
    LEGAL_SIGN_OTP_SENT, LEGAL_REQUEST_REOPENED,
    // FR-07: kích hoạt DMS và Executor
    DMS_TRIGGERED, DMS_TRIGGERED_NO_EXECUTOR, EXECUTOR_ACTIVATED, EXECUTOR_ACKNOWLEDGED,
    // X2: người xác nhận chéo DMS (Trusted Social Confirmers)
    DMS_CONFIRMER_ADDED, DMS_CONFIRMER_REMOVED,
    DMS_CONFIRMATION_REQUESTED, DMS_CONFIRMED_ALIVE, DMS_CONFIRMED_UNREACHABLE, DMS_CONFIRMATION_TIMEOUT,
    DMS_CONFIRMATION_UNDELIVERED,
    // FR-18 (phần 2): tải tài liệu pháp lý
    DOCUMENT_DOWNLOADED,
    // FR-16: thông báo bàn giao cho Beneficiary
    BENEFICIARY_NOTIFIED, BENEFICIARY_NOTIFY_FAILED, BENEFICIARY_NOTIFY_GAVE_UP, VAULT_ARCHIVED,
    // FR-03: cấu hình và check-in DMS
    DMS_CONFIGURED, DMS_CHECKIN,
    // F2: check-in một chạm từ link trong email nhắc (không đăng nhập)
    DMS_CHECKIN_ONE_CLICK,
    // FR-02: chỉ định người thụ hưởng
    BENEFICIARY_ASSIGNED, BENEFICIARY_REPLACED, BENEFICIARY_ASSIGN_DENIED, BENEFICIARY_LINKED, BENEFICIARY_REMOVED,
    // FR-02 (V8): gỡ người thụ hưởng bị từ chối
    BENEFICIARY_REMOVE_DENIED,
    // Owner sửa tên / mối quan hệ người thụ hưởng
    BENEFICIARY_UPDATED, BENEFICIARY_UPDATE_DENIED,
    LEGAL_VERIFICATION_REQUESTED,
    VERIFICATION_MESSAGE_SENT,
    // FR-06: Owner chỉ định / thay thế Executor (thu hồi dùng lại EXECUTOR_REVOKE)
    EXECUTOR_ASSIGNED, EXECUTOR_REPLACED,
    // FR-12: Verifier xem giấy chứng tử; FR-11: Verifier xem tệp đính kèm trong trao đổi với Executor
    LEGAL_DOCUMENT_VIEWED, VERIFICATION_ATTACHMENT_VIEWED,
    // FR-16 (Q25): Vault mở khóa khi Beneficiary chưa có tài khoản đã xác minh
    BENEFICIARY_PENDING_NOTIFIED,
    // FR-01/FR-27: Cập nhật thông tin Két sắt
    VAULT_UPDATED,
    // FR-04: Xóa tài liệu pháp lý
    DOCUMENT_DELETED,
    // FR-20: Admin xem chi tiết Két sắt
    ADMIN_VAULT_VIEWED,
    // F6 (M2): Executor xem danh sách tài sản của vault; Owner thấy trong tab "Ai đã xem"
    EXECUTOR_VAULT_VIEWED,
    // BR-05: xóa vĩnh viễn Vault sau 2 năm lưu trữ
    VAULT_PURGED,
    // O5: Admin tạm dừng / mở lại DMS
    DMS_SUSPENDED, DMS_RESUMED,
    // Q27 (B3): FR-07 hoãn kích hoạt vì Owner chưa nhận được lần nhắc nào
    DMS_TRIGGER_DEFERRED,
    // V4: Owner thêm / sửa / xóa tài sản số
    ASSET_CREATED, ASSET_UPDATED, ASSET_DELETED,
    // Hồ sơ cá nhân
    PROFILE_UPDATED, PASSWORD_CHANGED, PASSWORD_CHANGE_FAILED, AVATAR_DELETED, AVATAR_UPDATED,
    //Admin xác nhận đã xử lý cảnh báo bảo mật
    SECURITY_ALERT_ACKNOWLEDGED,    EXECUTOR_ASSIGNED_BY_ADMIN,
    // Toàn (PR admin 08/11): Admin gửi lại email lỗi, Admin đổi giá gói
    ADMIN_NOTIFICATION_RETRIED, ADMIN_PLAN_UPDATED,
    MFA_ENABLED, MFA_DISABLED, MFA_VERIFY_FAILED, MFA_BACKUP_CODE_USED,
    // N2b: người thụ hưởng từ chối, quyền nhận chuyển sang người dự phòng / không còn người nhận
    ASSET_ALLOCATION_FALLBACK, CLAIM_DECLINED, CLAIM_STALLED, CLAIM_ACCEPTED,
    CLAIM_EXTENDED,DOSSIER_DOWNLOADED,
    // N2-exec: Executor nhắc người thụ hưởng chưa phản hồi
    BENEFICIARY_REMINDER_SENT,
    // T1: Thanh toán VNPay & gói VIP
    PAYMENT_SUCCEEDED, PAYMENT_FAILED, PAYMENT_EXPIRED, PAYMENT_REFUNDED, ADMIN_VIP_GRANTED,
    // Việc 6: Owner hủy gia hạn cuối kỳ / bật lại
    SUBSCRIPTION_CANCELLED, SUBSCRIPTION_RESUMED,
    // Việc 4: mã giảm giá và dùng thử
    COUPON_CREATED, COUPON_UPDATED, TRIAL_STARTED,
    // X1: người thụ hưởng đồng ý mở tài sản JOINT_CONSENSUS
    ASSET_CONSENSUS_APPROVED,
    // F5: Executor cập nhật checklist xử lý tài khoản số
    EXECUTOR_TASK_UPDATED,
    // C.5: xác thực tài sản
    ASSET_VERIFICATION_STARTED, ASSET_VERIFICATION_VERIFIED, ASSET_VERIFICATION_FAILED,
    EXECUTOR_EVIDENCE_ADDED,
    LEGAL_DOCUMENT_OCR_ANALYZED,
    // PR 2: xác nhận chéo Executor, bản chụp bàn giao, hết hạn / nhắc xác thực lại
    EXECUTOR_TASK_CONFIRMED, EXECUTOR_TASK_DISPUTED,
    HANDOVER_SNAPSHOT_CREATED,
    ASSET_VERIFICATION_EXPIRED, ASSET_VERIFICATION_REMINDED,
    // #10: Admin mở khóa xác minh danh tính của người thụ hưởng
    IDENTITY_UNLOCKED_BY_ADMIN,
    // Trang Hồ sơ: người dùng tự đăng xuất một / mọi thiết bị khác
    SESSION_REVOKED,
    // Trang Hồ sơ: người dùng tự xoá tài khoản (thành công / bị chặn / sai xác nhận)
    ACCOUNT_DELETED, ACCOUNT_DELETE_FAILED
}
