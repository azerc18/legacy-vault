package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.auditdto.AdminAuditLogResponse;
import com.ltld.app.legacyvault.dto.auditdto.SecurityAlertResponse;
import com.ltld.app.legacyvault.service.auditservice.AdminAuditLogService;
import com.ltld.app.legacyvault.service.auditservice.SecurityAlertService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** FR-20: Admin giám sát audit log và cảnh báo truy cập bất thường. */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN_MANAGE')")
public class AdminMonitoringController {

    private final AdminAuditLogService adminAuditLogService;
    private final SecurityAlertService securityAlertService;

    @GetMapping("/audit-logs")
    public ResponseEntity<ApiResponse<Page<AdminAuditLogResponse>>> searchAuditLogs(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String actorEmail,
            @RequestParam(required = false) String ip,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.success("Audit logs retrieved successfully",
                adminAuditLogService.search(action, actorEmail, ip, from, to, page, size)));
    }

    @GetMapping("/audit-logs/actions")
    public ResponseEntity<ApiResponse<List<String>>> listAuditActions() {
        return ResponseEntity.ok(ApiResponse.success(adminAuditLogService.listActions()));
    }

    @GetMapping("/security-alerts")
    public ResponseEntity<ApiResponse<Page<SecurityAlertResponse>>> listAlerts(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success("Alerts retrieved successfully",
                securityAlertService.list(status, page, size)));
    }

    @GetMapping("/security-alerts/count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> countOpenAlerts() {
        return ResponseEntity.ok(ApiResponse.success(Map.of("open", securityAlertService.countOpen())));
    }

    @PatchMapping("/security-alerts/{alertId}/acknowledge")
    public ResponseEntity<ApiResponse<SecurityAlertResponse>> acknowledgeAlert(
            Principal principal, @PathVariable UUID alertId) {
        UUID adminId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(ApiResponse.success("Alert acknowledged",
                securityAlertService.acknowledge(adminId, alertId)));
    }
}