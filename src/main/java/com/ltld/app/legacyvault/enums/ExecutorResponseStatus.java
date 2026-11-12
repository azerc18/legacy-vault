package com.ltld.app.legacyvault.enums;

/**
 * Executor phản hồi lời mời của Owner. Chỉ người đã ACCEPTED mới được tính là Executor dùng được
 * (bật DMS, được trigger kích hoạt, nhận leo thang). DECLINED luôn đi kèm ExecutorStatus.REVOKED.
 */
public enum ExecutorResponseStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    /** Hết hạn trả lời: hệ thống tự thu hồi, kèm ExecutorStatus.REVOKED như DECLINED. */
    EXPIRED
}
