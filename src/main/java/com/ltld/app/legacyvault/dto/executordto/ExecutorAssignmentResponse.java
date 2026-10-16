package com.ltld.app.legacyvault.dto.executordto;

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
    private LocalDateTime revokedAt;
}
