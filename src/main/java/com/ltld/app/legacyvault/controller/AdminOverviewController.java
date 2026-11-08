package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.adminoverviewdto.AdminStatsResponse;
import com.ltld.app.legacyvault.service.adminoverviewservice.AdminStatsService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** A1: số liệu trang tổng quan Admin. */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN_MANAGE')")
public class AdminOverviewController {

    private final AdminStatsService adminStatsService;

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<AdminStatsResponse>> getStats() {
        return ResponseEntity.ok(ApiResponse.success(adminStatsService.getStats()));
    }
}
