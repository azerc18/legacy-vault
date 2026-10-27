package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.adminuserdto.*;
import com.ltld.app.legacyvault.dto.dmsdto.DmsConfigResponse;
import com.ltld.app.legacyvault.service.adminuserservice.AdminRoleService;
import com.ltld.app.legacyvault.service.adminuserservice.AdminUserService;
import com.ltld.app.legacyvault.service.dmsservice.DmsConfigService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN_MANAGE')")
public class AdminController {
    private final AdminUserService adminUserService;
    private final DmsConfigService dmsConfigService;
    private final AdminRoleService adminRoleService;

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

    @PutMapping("/dms/{ownerId}/suspend")
    public ResponseEntity<ApiResponse<DmsConfigResponse>> suspendDms(
            Principal principal, @PathVariable UUID ownerId,
            @Valid @RequestBody(required = false) SuspendDmsRequest request) {
        UUID adminId = UUID.fromString(principal.getName());
        String reason = request == null ? null : request.getReason();
        return ResponseEntity.ok(ApiResponse.success("Tạm dừng DMS thành công",
                dmsConfigService.suspendByAdmin(ownerId, adminId, reason)));
    }

    @PutMapping("/dms/{ownerId}/resume")
    public ResponseEntity<ApiResponse<DmsConfigResponse>> resumeDms(
            Principal principal, @PathVariable UUID ownerId) {
        UUID adminId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(ApiResponse.success("Mở lại DMS thành công",
                dmsConfigService.resumeByAdmin(ownerId, adminId)));
    }

    @GetMapping("/authorities")
    public ResponseEntity<ApiResponse<List<AuthorityResponse>>> listAuthorities() {
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách quyền thành công",
                adminRoleService.listAuthorities()));
    }

    @GetMapping("/roles")
    public ResponseEntity<ApiResponse<List<RoleResponse>>> listRoles() {
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách role thành công",
                adminRoleService.listRoles()));
    }

    @PostMapping("/roles")
    public ResponseEntity<ApiResponse<RoleResponse>> createRole(
            Principal principal, @Valid @RequestBody CreateRoleRequest request) {
        UUID adminId = UUID.fromString(principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Tạo role thành công",
                adminRoleService.createRole(adminId, request)));
    }

    @PutMapping("/roles/{roleCode}")
    public ResponseEntity<ApiResponse<RoleResponse>> updateRole(
            Principal principal, @PathVariable String roleCode, @Valid @RequestBody UpdateRoleRequest request) {
        UUID adminId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(ApiResponse.success("Cập nhật role thành công",
                adminRoleService.updateRole(adminId, roleCode, request)));
    }

    @DeleteMapping("/roles/{roleCode}")
    public ResponseEntity<ApiResponse<Void>> deleteRole(Principal principal, @PathVariable String roleCode) {
        UUID adminId = UUID.fromString(principal.getName());
        adminRoleService.deleteRole(adminId, roleCode);
        return ResponseEntity.ok(ApiResponse.success("Xóa role thành công"));
    }
}
