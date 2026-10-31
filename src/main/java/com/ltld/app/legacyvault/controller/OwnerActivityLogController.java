package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.auditdto.VaultActivityLogResponse;
import com.ltld.app.legacyvault.exception.DmsException;
import com.ltld.app.legacyvault.service.auditservice.OwnerActivityLogService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

// FR-05 & F6: nhật ký cấp Owner cho các sự kiện DMS/Executor và nhật ký minh bạch "Ai đã xem gì".
@RestController
@RequestMapping("/api/owner/activity-logs")
@PreAuthorize("hasRole('OWNER')")
@RequiredArgsConstructor
public class OwnerActivityLogController {

    private final OwnerActivityLogService ownerActivityLogService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<VaultActivityLogResponse>>> getOwnerLogs(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Principal principal) {

        UUID ownerId = UUID.fromString(principal.getName());
        validatePagination(page, size);

        Page<VaultActivityLogResponse> response = ownerActivityLogService.getOwnerActivityLogs(
                ownerId, startDate, endDate, PageRequest.of(page, size));

        return ResponseEntity.ok(ApiResponse.success("Activity logs retrieved successfully", response));
    }

    /** F6: Nhật ký minh bạch "Ai đã xem gì" (Who Accessed My Estate). */
    @GetMapping("/access")
    public ResponseEntity<ApiResponse<Page<VaultActivityLogResponse>>> getEstateAccessLogs(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Principal principal) {

        UUID ownerId = UUID.fromString(principal.getName());
        validatePagination(page, size);

        Page<VaultActivityLogResponse> response = ownerActivityLogService.getEstateAccessLogs(
                ownerId, startDate, endDate, PageRequest.of(page, size));

        return ResponseEntity.ok(ApiResponse.success("Estate access logs retrieved successfully", response));
    }

    private static void validatePagination(int page, int size) {
        if (page < 0) {
            throw new DmsException("Invalid page number (must be >= 0).", HttpStatus.BAD_REQUEST);
        }
        if (size < 1 || size > 100) {
            throw new DmsException("Page size must be between 1 and 100.", HttpStatus.BAD_REQUEST);
        }
    }
}
