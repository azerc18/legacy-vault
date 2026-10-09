package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.auditdto.VaultActivityLogResponse;
import com.ltld.app.legacyvault.service.auditservice.VaultActivityLogService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@RestController
@RequestMapping("/api/vaults/{vaultId}/activity-logs")
@RequiredArgsConstructor
public class VaultActivityLogController {

    private final VaultActivityLogService vaultActivityLogService;

    @GetMapping
    @PreAuthorize("hasAuthority('VAULT_READ')")
    public ResponseEntity<ApiResponse<Page<VaultActivityLogResponse>>> getVaultLogs(
            @PathVariable UUID vaultId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Principal principal) {
            
        UUID requesterId = UUID.fromString(principal.getName());
        
        // Kiểm tra khoảng thời gian tối đa 1 năm (theo yêu cầu code review)
        if (startDate != null && endDate != null) {
            long days = ChronoUnit.DAYS.between(startDate, endDate);
            if (days > 365) {
                throw new com.ltld.app.legacyvault.exception.VaultException("Khoảng thời gian tối đa 1 năm.");
            }
        }
        
        Pageable pageable = PageRequest.of(page, size);
        Page<VaultActivityLogResponse> response = vaultActivityLogService.getVaultActivityLogs(vaultId, requesterId, startDate, endDate, pageable);

        return ResponseEntity.ok(ApiResponse.success("Fetched vault activity logs successfully", response));
    }
}
