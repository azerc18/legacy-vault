package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.AdminUserResponse;
import com.ltld.app.legacyvault.service.adminuserservice.AdminUserService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import lombok.RequiredArgsConstructor;
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
}
