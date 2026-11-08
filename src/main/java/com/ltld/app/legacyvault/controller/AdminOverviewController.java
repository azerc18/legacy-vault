package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.adminoverviewdto.AdminChartsResponse;
import com.ltld.app.legacyvault.dto.adminoverviewdto.AdminStatsResponse;
import com.ltld.app.legacyvault.dto.adminoverviewdto.AdminUserDetailResponse;
import com.ltld.app.legacyvault.dto.adminoverviewdto.AdminVaultRowResponse;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.service.adminoverviewservice.AdminInsightService;
import com.ltld.app.legacyvault.service.adminoverviewservice.AdminStatsService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** A1 tổng quan, A3 chi tiết người dùng, B2 tìm kho, C2 biểu đồ. Tất cả chỉ đọc. */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN_MANAGE')")
public class AdminOverviewController {

    private final AdminStatsService adminStatsService;
    private final AdminInsightService adminInsightService;

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<AdminStatsResponse>> getStats() {
        return ResponseEntity.ok(ApiResponse.success(adminStatsService.getStats()));
    }

    @GetMapping("/stats/charts")
    public ResponseEntity<ApiResponse<AdminChartsResponse>> getCharts() {
        return ResponseEntity.ok(ApiResponse.success(adminInsightService.getCharts()));
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<ApiResponse<AdminUserDetailResponse>> getUserDetail(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.success(adminInsightService.getUserDetail(userId)));
    }

    @GetMapping("/vaults")
    public ResponseEntity<ApiResponse<Page<AdminVaultRowResponse>>> searchVaults(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) VaultStatus status,
            @RequestParam(required = false) Integer expiringWithinDays,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                adminInsightService.searchVaults(q, status, expiringWithinDays, page, size)));
    }
}
