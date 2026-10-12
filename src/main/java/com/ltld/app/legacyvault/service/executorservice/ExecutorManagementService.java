package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.dto.executordto.AssignExecutorRequest;
import com.ltld.app.legacyvault.dto.executordto.ExecutorAssignmentResponse;
import com.ltld.app.legacyvault.enums.ExecutorType;

import java.util.List;
import java.util.UUID;

/**
 * FR-06: Owner chỉ định, thay thế hoặc thu hồi Executor (chính/dự phòng) khi DMS chưa kích hoạt.
 *
 * <p>Role EXECUTOR trên tài khoản chỉ phục vụ phân quyền thô (menu, endpoint). Quyền truy cập thật
 * vào dữ liệu của một Owner PHẢI dựa vào {@code ExecutorAssignment.status}, không dựa vào role trong JWT:
 * access token cũ vẫn mang role EXECUTOR cho đến khi hết hạn, kể cả sau khi assignment đã bị thu hồi.
 */
public interface ExecutorManagementService {

    ExecutorAssignmentResponse assignExecutor(UUID ownerId, ExecutorType type, AssignExecutorRequest request);

    ExecutorAssignmentResponse revokeExecutor(UUID ownerId, ExecutorType type);

    /** Các vị trí Executor của Owner (PRIMARY trước BACKUP), gồm cả vị trí đã REVOKED. */
    List<ExecutorAssignmentResponse> listExecutors(UUID ownerId);
}
