package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.adminuserdto.AdminUserResponse;
import com.ltld.app.legacyvault.dto.adminuserdto.UpdateUserStatusRequest;
import com.ltld.app.legacyvault.service.adminuserservice.AdminUserService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN_MANAGE')")
public class AdminController {
    private final AdminUserService adminUserService;

    @PostMapping("/users/{userId}/roles/{roleCode}")
    public ResponseEntity<ApiResponse<AdminUserResponse>> grantRole(
            Principal principal, @PathVariable UUID userId, @PathVariable String roleCode) {
        UUID adminId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(ApiResponse.success("Gán role thành công",
                adminUserService.grantRole(adminId, userId, roleCode)));
    }

    @DeleteMapping("/users/{userId}/roles/{roleCode}")
    public ResponseEntity<ApiResponse<AdminUserResponse>> revokeRole(
            Principal principal, @PathVariable UUID userId, @PathVariable String roleCode) {
        UUID adminId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(ApiResponse.success("Gỡ role thành công",
                adminUserService.revokeRole(adminId, userId, roleCode)));
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<Page<AdminUserResponse>>> listUsers(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách người dùng thành công",
                adminUserService.listUsers(q, role, status, page, size)));
    }

    @PatchMapping("/users/{userId}/status")
    public ResponseEntity<ApiResponse<AdminUserResponse>> updateStatus(
            Principal principal, @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserStatusRequest request) {
        UUID adminId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái thành công",
                adminUserService.updateStatus(adminId, userId, request)));
    }
}
