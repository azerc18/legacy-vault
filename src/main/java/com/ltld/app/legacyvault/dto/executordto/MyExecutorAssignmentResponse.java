package com.ltld.app.legacyvault.dto.executordto;

import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.ExecutorType;
import com.ltld.app.legacyvault.enums.LegalVerificationStatus;
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
    // B8: FE hiển thị thay cho ownerFullName khi Owner chưa có tên
    private String ownerEmail;
    private ExecutorType executorType;
    private ExecutorStatus status;
    private LocalDateTime assignedAt;
    private LocalDateTime activatedAt;
    private LocalDateTime acknowledgedAt;
    // B8: số liệu dashboard, để FE không phải gọi /vaults và /handover-progress cho từng Owner
    private long vaultCount;
    private LegalVerificationStatus latestRequestStatus;
    private long unreadMessageCount;
    // B8: activatedAt + backupActivationWaitDays; null khi chưa kích hoạt hoặc đã xác nhận
    private LocalDateTime acknowledgeDeadlineAt;
}
