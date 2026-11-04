package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.executordto.ExecutorTaskResponse;
import com.ltld.app.legacyvault.dto.executordto.UpdateExecutorTaskRequest;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.service.executorservice.ExecutorTaskService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

// F5: checklist xử lý tài khoản số. Controller riêng để không đụng ExecutorController / BeneficiaryController.
@RestController
@RequiredArgsConstructor
public class ExecutorTaskController {

    private final ExecutorTaskService executorTaskService;

    // Endpoint Executor không dùng @PreAuthorize: ExecutorAccessGuard (trong service) so người đăng nhập với
    // assignment và trả 404 nếu không phải của họ, giống các endpoint khác của ExecutorController.
    @GetMapping("/api/executor-assignments/{assignmentId}/tasks")
    public ResponseEntity<ApiResponse<List<ExecutorTaskResponse>>> listTasks(
            @PathVariable UUID assignmentId, Principal principal) {

        List<ExecutorTaskResponse> response = executorTaskService.listForExecutor(assignmentId, principal.getName());
        return ResponseEntity.ok(ApiResponse.success("Executor tasks retrieved", response));
    }

    @PatchMapping("/api/executor-assignments/{assignmentId}/tasks/{taskId}")
    public ResponseEntity<ApiResponse<ExecutorTaskResponse>> updateTask(
            @PathVariable UUID assignmentId, @PathVariable UUID taskId,
            @Valid @RequestBody UpdateExecutorTaskRequest request, Principal principal) {

        ExecutorTaskResponse response =
                executorTaskService.update(assignmentId, taskId, request, principal.getName());
        return ResponseEntity.ok(ApiResponse.success("Executor task updated", response));
    }

    // Beneficiary theo dõi Executor xử lý tài khoản số của Vault mình nhận
    @PreAuthorize("hasRole('BENEFICIARY')")
    @GetMapping("/api/beneficiaries/vaults/{vaultId}/executor-tasks")
    public ResponseEntity<ApiResponse<List<ExecutorTaskResponse>>> listTasksForBeneficiary(
            @PathVariable UUID vaultId, Principal principal) {

        List<ExecutorTaskResponse> response =
                executorTaskService.listForBeneficiary(vaultId, currentUserId(principal));
        return ResponseEntity.ok(ApiResponse.success("Executor tasks retrieved", response));
    }

    private static UUID currentUserId(Principal principal) {
        try {
            return UUID.fromString(principal.getName());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid session.");
        }
    }
}
