package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.entity.AuditLog;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AuditLog>>> getMyLogs(Principal principal) {
        // Lấy định danh của user từ Token
        UUID ownerId = UUID.fromString(principal.getName());

        // Gọi Service để kéo dữ liệu từ DB lên
        List<AuditLog> logs = auditLogService.getUserLogs(ownerId);

        return ResponseEntity.ok(ApiResponse.success("Fetched audit logs successfully", logs));
    }
}
