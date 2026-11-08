package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.beneficiarydto.DocumentDownloadResponse;
import com.ltld.app.legacyvault.dto.executordto.DisputeExecutorTaskRequest;
import com.ltld.app.legacyvault.dto.executordto.ExecutorTaskResponse;
import com.ltld.app.legacyvault.dto.executordto.UpdateExecutorTaskRequest;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.service.executorservice.ExecutorEvidenceService;
import com.ltld.app.legacyvault.service.executorservice.ExecutorTaskService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import java.util.UUID;

// F5: checklist xử lý tài khoản số. Controller riêng để không đụng ExecutorController / BeneficiaryController.
@RestController
@RequiredArgsConstructor
public class ExecutorTaskController {

    private final ExecutorTaskService executorTaskService;
    private final ExecutorEvidenceService executorEvidenceService;

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

    // C.4: tải / thay tệp bằng chứng (PDF, JPG, PNG, tối đa 10MB), lưu mã hóa
    @PostMapping(value = "/api/executor-assignments/{assignmentId}/tasks/{taskId}/evidence",
            consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<ExecutorTaskResponse>> uploadEvidence(
            @PathVariable UUID assignmentId, @PathVariable UUID taskId,
            @RequestParam("file") MultipartFile file, Principal principal) throws Exception {

        ExecutorTaskResponse response =
                executorEvidenceService.upload(assignmentId, taskId, file, principal.getName());
        return ResponseEntity.ok(ApiResponse.success("Evidence uploaded", response));
    }

    // C.4: Executor tải lại tệp bằng chứng đã giải mã
    @GetMapping("/api/executor-assignments/{assignmentId}/tasks/{taskId}/evidence/download")
    public ResponseEntity<byte[]> downloadEvidence(
            @PathVariable UUID assignmentId, @PathVariable UUID taskId, Principal principal) throws Exception {

        DocumentDownloadResponse file = executorEvidenceService.download(assignmentId, taskId, principal.getName());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.getFileName(), StandardCharsets.UTF_8)
                                .build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(file.getContent());
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

    // C.6: Beneficiary xác nhận kết quả Executor đã báo DONE
    @PreAuthorize("hasRole('BENEFICIARY')")
    @PostMapping("/api/beneficiaries/vaults/{vaultId}/executor-tasks/{taskId}/confirm")
    public ResponseEntity<ApiResponse<ExecutorTaskResponse>> confirmTask(
            @PathVariable UUID vaultId, @PathVariable UUID taskId, Principal principal) {

        ExecutorTaskResponse response =
                executorTaskService.confirmByBeneficiary(vaultId, taskId, currentUserId(principal));
        return ResponseEntity.ok(ApiResponse.success("Executor task confirmed", response));
    }

    // C.6: Beneficiary phản đối trong thời hạn; task quay về DOING
    @PreAuthorize("hasRole('BENEFICIARY')")
    @PostMapping("/api/beneficiaries/vaults/{vaultId}/executor-tasks/{taskId}/dispute")
    public ResponseEntity<ApiResponse<ExecutorTaskResponse>> disputeTask(
            @PathVariable UUID vaultId, @PathVariable UUID taskId,
            @Valid @RequestBody DisputeExecutorTaskRequest request, Principal principal) {

        ExecutorTaskResponse response =
                executorTaskService.disputeByBeneficiary(vaultId, taskId, request, currentUserId(principal));
        return ResponseEntity.ok(ApiResponse.success("Executor task disputed", response));
    }

    private static UUID currentUserId(Principal principal) {
        try {
            return UUID.fromString(principal.getName());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid session.");
        }
    }
}
