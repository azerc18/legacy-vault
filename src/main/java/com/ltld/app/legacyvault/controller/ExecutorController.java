package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.executordto.CreateDeathVerificationRequest;
import com.ltld.app.legacyvault.dto.executordto.DeathVerificationResponse;
import com.ltld.app.legacyvault.dto.executordto.ExecutorActivationResponse;
import com.ltld.app.legacyvault.dto.executordto.ExecutorAssignedVaultsResponse;
import com.ltld.app.legacyvault.service.dmsservice.DmsAutomationService;
import com.ltld.app.legacyvault.service.executorservice.ExecutorAssetService;
import com.ltld.app.legacyvault.service.executorservice.ExecutorVerificationService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/executor-assignments")
@RequiredArgsConstructor
public class ExecutorController {

    private final DmsAutomationService dmsAutomationService;
    // FR-09: Service xử lý nộp yêu cầu xác minh cái chết
    private final ExecutorVerificationService executorVerificationService;
    // FR-08: Service xử lý xem danh sách tài sản được ủy quyền quản lý
    private final ExecutorAssetService executorAssetService;

    // FR-07 bước 4: Executor xác nhận đã nhận thông báo kích hoạt.
    // Principal.getName() là định danh của phiên đăng nhập (email hoặc user id); Auth/JWT do module khác cung cấp.
    // assignmentId là id của ExecutorAssignment, không phải user id của Executor.
    @PostMapping("/{assignmentId}/acknowledge")
    public ResponseEntity<ApiResponse<ExecutorActivationResponse>> acknowledge(
            @PathVariable UUID assignmentId, Principal principal) {

        ExecutorActivationResponse response =
                dmsAutomationService.acknowledgeActivation(assignmentId, principal.getName());
        return ResponseEntity.ok(ApiResponse.success("Activation acknowledged", response));
    }

    // FR-08: Xem danh sách tài sản được ủy quyền quản lý và hướng dẫn bàn giao
    @GetMapping("/{assignmentId}/vaults")
    public ResponseEntity<ApiResponse<ExecutorAssignedVaultsResponse>> getAssignedVaults(
            @PathVariable UUID assignmentId, Principal principal) {

        ExecutorAssignedVaultsResponse response =
                executorAssetService.getAssignedVaults(assignmentId, principal.getName());
        return ResponseEntity.ok(ApiResponse.success("Assigned vaults retrieved", response));
    }

    // FR-09: Digital Executor nộp hồ sơ xác minh cái chết (Death Verification Request) kèm file chứng tử mã hóa
    @PostMapping(value = "/{assignmentId}/verification-requests", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DeathVerificationResponse>> submitDeathVerification(
            @PathVariable UUID assignmentId,
            @RequestPart("file") MultipartFile file,
            @Valid @ModelAttribute CreateDeathVerificationRequest request,
            Principal principal) {

        DeathVerificationResponse response =
                executorVerificationService.submit(assignmentId, principal.getName(), file, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Verification request submitted", response));
    }
}
