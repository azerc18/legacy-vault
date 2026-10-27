package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.executordto.AssignExecutorRequest;
import com.ltld.app.legacyvault.dto.executordto.ExecutorAssignmentResponse;
import com.ltld.app.legacyvault.enums.ExecutorType;
import com.ltld.app.legacyvault.exception.ExecutorManagementException;
import com.ltld.app.legacyvault.service.executorservice.ExecutorManagementService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * FR-06: Owner quản lý Executor chính (PRIMARY) và dự phòng (BACKUP) của mình.
 * <p>Mail báo chỉ định/thu hồi chỉ mang tính thông tin: gửi sau commit, gửi lỗi chỉ log warn và không gửi lại, nên FE không được hứa "Executor chắc chắn đã nhận được email".
 */
@RestController
@RequestMapping("/api/owner/executors")
@RequiredArgsConstructor
public class OwnerExecutorController {

    private final ExecutorManagementService executorManagementService;

    // Danh sách vị trí Executor của Owner (gồm cả vị trí đã thu hồi)
    @GetMapping
    @PreAuthorize("hasAuthority('EXECUTOR_MANAGE')")
    public ResponseEntity<ApiResponse<List<ExecutorAssignmentResponse>>> listExecutors(Principal principal) {
        UUID ownerId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(ApiResponse.success("Executors retrieved successfully",
                executorManagementService.listExecutors(ownerId)));
    }

    // Chỉ định hoặc thay thế Executor cho vị trí {type}
    @PutMapping("/{type}")
    @PreAuthorize("hasAuthority('EXECUTOR_MANAGE')")
    public ResponseEntity<ApiResponse<ExecutorAssignmentResponse>> assignExecutor(
            Principal principal,
            @PathVariable String type,
            @Valid @RequestBody AssignExecutorRequest request) {

        UUID ownerId = UUID.fromString(principal.getName());
        ExecutorAssignmentResponse response =
                executorManagementService.assignExecutor(ownerId, parseType(type), request);
        return ResponseEntity.ok(ApiResponse.success("Executor updated successfully", response));
    }

    // Thu hồi Executor đang giữ vị trí {type}
    @DeleteMapping("/{type}")
    @PreAuthorize("hasAuthority('EXECUTOR_MANAGE')")
    public ResponseEntity<ApiResponse<ExecutorAssignmentResponse>> revokeExecutor(
            Principal principal,
            @PathVariable String type) {

        UUID ownerId = UUID.fromString(principal.getName());
        ExecutorAssignmentResponse response = executorManagementService.revokeExecutor(ownerId, parseType(type));
        return ResponseEntity.ok(ApiResponse.success("Executor revoked successfully", response));
    }

    // Tự parse thay vì để Spring convert enum: giá trị sai trả 400 rõ ràng thay vì rơi vào handler 500 chung.
    private static ExecutorType parseType(String type) {
        try {
            return ExecutorType.valueOf(type.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ExecutorManagementException("Invalid executor type (PRIMARY or BACKUP).",
                    HttpStatus.BAD_REQUEST);
        }
    }
}
