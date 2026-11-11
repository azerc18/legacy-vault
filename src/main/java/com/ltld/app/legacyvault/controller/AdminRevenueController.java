package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.adminoverviewdto.AdminRevenueResponse;
import com.ltld.app.legacyvault.service.adminoverviewservice.AdminRevenueService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Việc 5: báo cáo doanh thu (chỉ đọc). Controller riêng để không đụng các API Admin đã có. */
@RestController
@RequestMapping("/api/admin/revenue")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN_MANAGE')")
public class AdminRevenueController {

    private final AdminRevenueService adminRevenueService;

    /** {@code range}: {@code 30d} (theo ngày, mặc định) hoặc {@code 12m} (theo tháng). */
    @GetMapping
    public ResponseEntity<ApiResponse<AdminRevenueResponse>> report(@RequestParam(defaultValue = "30d") String range) {
        return ResponseEntity.ok(ApiResponse.success(adminRevenueService.report(range)));
    }
}
