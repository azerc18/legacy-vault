package com.ltld.app.legacyvault.dto.executordto;

import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.ExecutorType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/** B1: một assignment của chính Executor đang đăng nhập; FE dùng assignmentId cho mọi API executor-assignments khác. */
@Getter
@Builder
public class MyExecutorAssignmentResponse {
    private UUID assignmentId;
    private String ownerFullName;
    private ExecutorType executorType;
    private ExecutorStatus status;
    private LocalDateTime assignedAt;
    private LocalDateTime activatedAt;
    private LocalDateTime acknowledgedAt;
}
