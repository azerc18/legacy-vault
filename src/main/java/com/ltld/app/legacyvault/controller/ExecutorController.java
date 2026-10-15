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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/executor-assignments")
@RequiredArgsConstructor
public class ExecutorController {

    private final DmsAutomationService dmsAutomationService;
    private final ExecutorAssetService executorAssetService;
    private final ExecutorVerificationService executorVerificationService;

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

    // FR-08: Executor xem danh sách Vault/tài sản của Owner và hướng dẫn bàn giao (chỉ metadata, không giải mã)
    @GetMapping("/{assignmentId}/vaults")
    public ResponseEntity<ApiResponse<ExecutorAssignedVaultsResponse>> getAssignedVaults(
            @PathVariable UUID assignmentId, Principal principal) {

        ExecutorAssignedVaultsResponse response =
                executorAssetService.getAssignedVaults(assignmentId, principal.getName());
        return ResponseEntity.ok(ApiResponse.success("Assigned vaults retrieved", response));
    }

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
