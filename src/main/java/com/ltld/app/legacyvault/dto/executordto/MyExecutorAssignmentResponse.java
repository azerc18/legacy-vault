package com.ltld.app.legacyvault.dto.executordto;

import com.ltld.app.legacyvault.enums.ExecutorResponseStatus;
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
    // FR-07/FR-14: FE hiển thị thay cho ownerFullName khi Owner chưa có tên
    private String ownerEmail;
    private ExecutorType executorType;
    private ExecutorStatus status;
    // Lời mời: PENDING = chưa đồng ý (FE hiện ở khối "Lời mời đang chờ"), ACCEPTED = đã nhận trách nhiệm
    private ExecutorResponseStatus responseStatus;
    private LocalDateTime assignedAt;
    private LocalDateTime activatedAt;
    private LocalDateTime acknowledgedAt;
    // FR-07/FR-14: số liệu dashboard, để FE không phải gọi /vaults và /handover-progress cho từng Owner
    private long vaultCount;
    private LegalVerificationStatus latestRequestStatus;
    private long unreadMessageCount;
    // FR-07/FR-14: activatedAt + backupActivationWaitDays; chỉ Primary, null khi chưa kích hoạt hoặc đã xác nhận
    private LocalDateTime acknowledgeDeadlineAt;
}
