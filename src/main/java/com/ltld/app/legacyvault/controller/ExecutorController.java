package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.executordto.ExecutorActivationResponse;
import com.ltld.app.legacyvault.service.dmsservice.DmsAutomationService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/executor-assignments")
@RequiredArgsConstructor
public class ExecutorController {

    private final DmsAutomationService dmsAutomationService;

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
}
