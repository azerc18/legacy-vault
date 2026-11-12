package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.enums.ExecutorType;

import java.util.UUID;

/**
 * Executor vừa đồng ý / từ chối lời mời của Owner. Listener (AFTER_COMMIT) gửi mail và ghi thông báo trên chuông
 * cho Owner; transaction rollback thì không gửi gì.
 *
 * @param declineReason lý do từ chối (có thể null); luôn null khi accepted = true
 */
public record ExecutorInvitationRespondedEvent(UUID assignmentId, UUID ownerId, String ownerEmail, String ownerName,
                                               String executorName, String executorEmail, ExecutorType executorType,
                                               boolean accepted, String declineReason) {

    /** "Tên (email)"; chưa có tên thì chỉ email. */
    public String executorLabel() {
        return executorName == null || executorName.isBlank()
                ? executorEmail : executorName + " (" + executorEmail + ")";
    }
}
