package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.executordto.CreateDeathVerificationRequest;
import com.ltld.app.legacyvault.dto.executordto.DeathVerificationResponse;
import com.ltld.app.legacyvault.dto.executordto.ExecutorActivationResponse;
import com.ltld.app.legacyvault.dto.executordto.ExecutorAssignedVaultsResponse;
import com.ltld.app.legacyvault.dto.executordto.SendVerificationMessageRequest;
import com.ltld.app.legacyvault.dto.executordto.VerificationMessageResponse;
import com.ltld.app.legacyvault.dto.executordto.VaultHandoverProgressResponse;
import com.ltld.app.legacyvault.service.dmsservice.DmsAutomationService;
import com.ltld.app.legacyvault.service.executorservice.ExecutorAssetService;
import com.ltld.app.legacyvault.service.executorservice.ExecutorHandoverService;
import com.ltld.app.legacyvault.service.executorservice.ExecutorVerificationService;
import com.ltld.app.legacyvault.service.executorservice.VerificationExchangeService;
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
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/executor-assignments")
@RequiredArgsConstructor
public class ExecutorController {

    private final DmsAutomationService dmsAutomationService;
    private final ExecutorAssetService executorAssetService;
    private final ExecutorHandoverService executorHandoverService;
    private final ExecutorVerificationService executorVerificationService;
    private final VerificationExchangeService verificationExchangeService;

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

    // FR-10: Executor theo dõi tiến độ bàn giao các Vault trong yêu cầu xác minh mình đã gửi
    @GetMapping("/{assignmentId}/handover-progress")
    public ResponseEntity<ApiResponse<List<VaultHandoverProgressResponse>>> getHandoverProgress(
            @PathVariable UUID assignmentId, Principal principal) {

        List<VaultHandoverProgressResponse> response =
                executorHandoverService.getHandoverProgress(assignmentId, principal.getName());
        return ResponseEntity.ok(ApiResponse.success("Handover progress retrieved", response));
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

    // FR-11: Executor gửi nội dung hoặc tệp bổ sung cho Legal Verifier trong yêu cầu xác minh của mình
    @PostMapping(value = "/{assignmentId}/verification-requests/{requestId}/messages",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<VerificationMessageResponse>> sendVerificationMessage(
            @PathVariable UUID assignmentId,
            @PathVariable UUID requestId,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @Valid @ModelAttribute SendVerificationMessageRequest request,
            Principal principal) {

        VerificationMessageResponse response =
                verificationExchangeService.send(assignmentId, requestId, principal.getName(), file, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Verification message sent", response));
    }

    // FR-11: lịch sử trao đổi của một yêu cầu xác minh
    @GetMapping("/{assignmentId}/verification-requests/{requestId}/messages")
    public ResponseEntity<ApiResponse<List<VerificationMessageResponse>>> getVerificationMessages(
            @PathVariable UUID assignmentId, @PathVariable UUID requestId, Principal principal) {

        List<VerificationMessageResponse> response =
                verificationExchangeService.history(assignmentId, requestId, principal.getName());
        return ResponseEntity.ok(ApiResponse.success("Verification messages retrieved", response));
    }
}
