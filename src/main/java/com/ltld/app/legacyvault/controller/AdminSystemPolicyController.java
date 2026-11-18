package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.systempolicydto.AdminDmsPolicyResponse;
import com.ltld.app.legacyvault.dto.systempolicydto.SystemInfoResponse;
import com.ltld.app.legacyvault.dto.systempolicydto.UpdateDmsPolicyRequest;
import com.ltld.app.legacyvault.service.systempolicyservice.DmsPolicyService;
import com.ltld.app.legacyvault.service.systempolicyservice.SystemInfoService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

/** Trang Chính sách hệ thống: Admin chỉnh mặc định / giới hạn DMS và xem thông tin mã hoá, lưu trữ, sao lưu. */
@RestController
@RequestMapping("/api/admin/system-policies")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN_MANAGE')")
public class AdminSystemPolicyController {

    private final DmsPolicyService dmsPolicyService;
    private final SystemInfoService systemInfoService;

    @GetMapping("/dms")
    public ResponseEntity<ApiResponse<AdminDmsPolicyResponse>> getDms() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(dmsPolicyService.getForAdmin()));
    }

    @PutMapping("/dms")
    public ResponseEntity<ApiResponse<AdminDmsPolicyResponse>> updateDms(Principal principal,
                                                                         @Valid @RequestBody UpdateDmsPolicyRequest request) {
        UUID adminId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(ApiResponse.success("System policy saved", dmsPolicyService.update(adminId, request)));
    }

    @GetMapping("/system-info")
    public ResponseEntity<ApiResponse<SystemInfoResponse>> systemInfo() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(systemInfoService.get()));
    }
}
