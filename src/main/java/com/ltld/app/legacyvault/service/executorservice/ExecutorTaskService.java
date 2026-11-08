package com.ltld.app.legacyvault.service.executorservice;

import com.ltld.app.legacyvault.dto.executordto.DisputeExecutorTaskRequest;
import com.ltld.app.legacyvault.dto.executordto.ExecutorTaskResponse;
import com.ltld.app.legacyvault.dto.executordto.UpdateExecutorTaskRequest;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

// F5: checklist Executor xử lý tài khoản số theo di nguyện (EXECUTOR_DISPOSITION)
public interface ExecutorTaskService {

    /** Sinh task cho mọi tài sản EXECUTOR_DISPOSITION chưa có task trong các Vault; gọi lại nhiều lần vẫn an toàn. */
    int generateForVaults(Collection<UUID> vaultIds);

    List<ExecutorTaskResponse> listForExecutor(UUID assignmentId, String executorLogin);

    ExecutorTaskResponse update(UUID assignmentId, UUID taskId, UpdateExecutorTaskRequest request,
                                String executorLogin);

    /** Beneficiary chỉ xem tiến độ của Vault mình được nhận. */
    List<ExecutorTaskResponse> listForBeneficiary(UUID vaultId, UUID currentUserId);

    /** C.6: Beneficiary xác nhận task Executor đã báo DONE (gọi lại khi đã xác nhận thì trả về như cũ). */
    ExecutorTaskResponse confirmByBeneficiary(UUID vaultId, UUID taskId, UUID currentUserId);

    /** C.6: Beneficiary phản đối trong thời hạn; task quay về DOING và Executor được báo trong app. */
    ExecutorTaskResponse disputeByBeneficiary(UUID vaultId, UUID taskId, DisputeExecutorTaskRequest request,
                                              UUID currentUserId);
}
