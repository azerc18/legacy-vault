package com.ltld.app.legacyvault.dto.executordto;

import com.ltld.app.legacyvault.enums.ExecutorResponseStatus;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.ExecutorType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/** FR-06: một vị trí Executor (chính/dự phòng) của Owner. */
@Getter
@Builder
public class ExecutorAssignmentResponse {
    private UUID assignmentId;
    private ExecutorType executorType;
    private ExecutorStatus status;
    private String email;
    private String fullName;
    private LocalDateTime assignedAt;
    private LocalDateTime activatedAt;
    // FR-06: Owner thấy Executor đã xác nhận bàn giao hay chưa (null = chưa xác nhận)
    private LocalDateTime acknowledgedAt;
    private LocalDateTime revokedAt;
    // Lời mời: PENDING (chờ Executor phản hồi) / ACCEPTED / DECLINED (kèm REVOKED); declineReason có thể null
    private ExecutorResponseStatus responseStatus;
    private LocalDateTime respondedAt;
    private String declineReason;
    // Hạn trả lời lời mời; chỉ có nghĩa khi responseStatus = PENDING
    private LocalDateTime invitationExpiresAt;
}
