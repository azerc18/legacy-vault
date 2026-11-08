package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.auditdto.AcknowledgeAlertRequest;
import com.ltld.app.legacyvault.dto.auditdto.AdminAuditLogResponse;
import com.ltld.app.legacyvault.dto.auditdto.SecurityAlertResponse;
import com.ltld.app.legacyvault.service.auditservice.AdminAuditLogService;
import com.ltld.app.legacyvault.service.auditservice.SecurityAlertService;
import com.ltld.app.legacyvault.service.adminoverviewservice.CsvWriter;
import com.ltld.app.legacyvault.utility.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Instant;
import java.time.LocalDate;
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

    // B4: xuất CSV theo đúng bộ lọc đang chọn (tối đa 10.000 dòng)
    @GetMapping("/audit-logs/export")
    public ResponseEntity<byte[]> exportAuditLogs(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String actorEmail,
            @RequestParam(required = false) String ip,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        List<AdminAuditLogResponse> rows = adminAuditLogService.export(action, actorEmail, ip, from, to);
        CsvWriter csv = new CsvWriter().row("created_at", "action", "result", "actor_email", "actor_id",
                "entity_type", "entity_id", "vault_id", "ip", "detail", "user_agent");
        for (AdminAuditLogResponse r : rows) {
            csv.row(r.getCreatedAt(), r.getAction(), r.getResult(), r.getActorEmail(), r.getActorId(),
                    r.getEntityType(), r.getEntityId(), r.getVaultId(), r.getIpAddress(), r.getDetail(), r.getUserAgent());
        }
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("audit-logs-" + LocalDate.now() + ".csv", StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(csv.toBytes());
    }

    @GetMapping("/audit-logs/actions")
    public ResponseEntity<ApiResponse<List<String>>> listAuditActions() {
        return ResponseEntity.ok(ApiResponse.success(adminAuditLogService.listActions()));
    }

    @GetMapping("/security-alerts")
    public ResponseEntity<ApiResponse<Page<SecurityAlertResponse>>> listAlerts(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success("Alerts retrieved successfully",
                securityAlertService.list(status, severity, page, size)));
    }

    @GetMapping("/security-alerts/count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> countOpenAlerts() {
        return ResponseEntity.ok(ApiResponse.success(Map.of("open", securityAlertService.countOpen())));
    }

    @PatchMapping("/security-alerts/{alertId}/acknowledge")
    public ResponseEntity<ApiResponse<SecurityAlertResponse>> acknowledgeAlert(
            Principal principal, @PathVariable UUID alertId,
            @Valid @RequestBody(required = false) AcknowledgeAlertRequest request) {
        UUID adminId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(ApiResponse.success("Alert acknowledged",
                securityAlertService.acknowledge(adminId, alertId, request == null ? null : request.note())));
    }
}