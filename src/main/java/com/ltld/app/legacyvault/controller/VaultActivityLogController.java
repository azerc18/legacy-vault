package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.auditdto.VaultActivityLogResponse;
import com.ltld.app.legacyvault.entity.VaultActivityLog;
import com.ltld.app.legacyvault.service.auditservice.VaultActivityLogService;
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
@RequestMapping("/api/vault-activity-logs")
@RequiredArgsConstructor
public class VaultActivityLogController {

    private final VaultActivityLogService vaultActivityLogService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<VaultActivityLogResponse>>> getMyLogs(Principal principal) {
        UUID ownerId = UUID.fromString(principal.getName());
        List<VaultActivityLog> logs = vaultActivityLogService.getUserLogs(ownerId);
        
        List<VaultActivityLogResponse> response = logs.stream()
                .map(log -> VaultActivityLogResponse.builder()
                        .id(log.getId())
                        .actionType(log.getActionType())
                        .description(log.getDescription())
                        .ipAddress(log.getIpAddress())
                        .createdAt(log.getCreatedAt())
                        .build())
                .toList();

        return ResponseEntity.ok(ApiResponse.success("Fetched vault activity logs successfully", response));
    }
}
