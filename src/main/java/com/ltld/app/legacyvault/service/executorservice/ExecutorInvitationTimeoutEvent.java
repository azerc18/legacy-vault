package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.enums.ExecutorType;

import java.util.UUID;

/**
 * Lời mời làm Executor sắp hết hạn (REMINDER, báo người được mời) hoặc đã hết hạn và bị thu hồi
 * (EXPIRED, báo Owner qua mail và chuông). Phát ở {@link ExecutorInvitationExpiryScheduler};
 * listener chạy AFTER_COMMIT nên transaction rollback thì không gửi gì.
 *
 * @param days REMINDER: số ngày còn lại; EXPIRED: số ngày đã cho để trả lời
 */
public record ExecutorInvitationTimeoutEvent(Kind kind, UUID assignmentId, UUID ownerId, String ownerEmail,
                                             String ownerName, String executorEmail, String executorName,
                                             ExecutorType executorType, int days) {

    public enum Kind { REMINDER, EXPIRED }

    /** "Tên (email)"; chưa có tên thì chỉ email. */
    public String executorLabel() {
        return executorName == null || executorName.isBlank()
                ? executorEmail : executorName + " (" + executorEmail + ")";
    }
}
