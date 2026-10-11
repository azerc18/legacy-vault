package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.executordto.ExecutorActivationResponse;
import com.ltld.app.legacyvault.dto.executordto.SendVerificationMessageRequest;
import com.ltld.app.legacyvault.dto.executordto.VaultHandoverProgressResponse;
import com.ltld.app.legacyvault.dto.executordto.VerificationMessageResponse;
import com.ltld.app.legacyvault.service.dmsservice.DmsAutomationService;
import com.ltld.app.legacyvault.service.executorservice.ExecutorHandoverService;
import com.ltld.app.legacyvault.service.executorservice.VerificationExchangeService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/executors")
@RequiredArgsConstructor
public class ExecutorController {

    private final DmsAutomationService dmsAutomationService;
    private final ExecutorHandoverService executorHandoverService;
    private final VerificationExchangeService verificationExchangeService;

    // FR-07 bước 4: Executor xác nhận đã nhận thông báo kích hoạt.
    // Principal.getName() là định danh của phiên đăng nhập (email hoặc user id); Auth/JWT do module khác cung cấp.
    @PostMapping("/{executorId}/acknowledge")
    public ResponseEntity<ApiResponse<ExecutorActivationResponse>> acknowledge(
            @PathVariable UUID executorId, Principal principal) {

        ExecutorActivationResponse response =
                dmsAutomationService.acknowledgeActivation(executorId, principal.getName());
        return ResponseEntity.ok(ApiResponse.success("Activation acknowledged", response));
    }

    // FR-10: Executor theo dõi tiến độ bàn giao từng tài sản / Vault theo assignment ID
    @GetMapping("/{executorId}/handover-progress")
    public ResponseEntity<ApiResponse<List<VaultHandoverProgressResponse>>> getHandoverProgressByAssignment(
            @PathVariable UUID executorId, Principal principal) {

        List<VaultHandoverProgressResponse> response =
                executorHandoverService.getHandoverProgress(executorId, principal.getName());
        return ResponseEntity.ok(ApiResponse.success("Vault handover progress retrieved successfully", response));
    }

    // FR-10: Executor theo dõi tiến độ bàn giao các Vault được ủy quyền theo phiên đăng nhập hiện tại
    @GetMapping("/handover-progress")
    public ResponseEntity<ApiResponse<List<VaultHandoverProgressResponse>>> getHandoverProgress(
            Principal principal) {

        List<VaultHandoverProgressResponse> response =
                executorHandoverService.getHandoverProgress(principal.getName());
        return ResponseEntity.ok(ApiResponse.success("Vault handover progress retrieved successfully", response));
    }

    // FR-11: Executor gửi tin nhắn hoặc bổ sung tài liệu vào luồng xác minh
    @PostMapping("/verifications/{verificationId}/messages")
    public ResponseEntity<ApiResponse<VerificationMessageResponse>> sendVerificationMessage(
            @PathVariable UUID verificationId,
            @Valid @RequestBody SendVerificationMessageRequest request,
            Principal principal) {

        VerificationMessageResponse response =
                verificationExchangeService.sendExecutorMessage(verificationId, request, principal.getName());
        return ResponseEntity.ok(ApiResponse.success("Verification message sent successfully", response));
    }

    // FR-11: Executor xem lịch sử trao đổi tài liệu xác minh
    @GetMapping("/verifications/{verificationId}/messages")
    public ResponseEntity<ApiResponse<List<VerificationMessageResponse>>> getVerificationMessages(
            @PathVariable UUID verificationId,
            Principal principal) {

        List<VerificationMessageResponse> response =
                verificationExchangeService.getExchangeMessages(verificationId, principal.getName());
        return ResponseEntity.ok(ApiResponse.success("Verification messages retrieved successfully", response));
    }
}
