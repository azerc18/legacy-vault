package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.readinessdto.ReadinessResponse;
import com.ltld.app.legacyvault.service.readinessservice.OwnerReadinessService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

/**
 * F1: Điểm sẵn sàng thừa kế (Estate Readiness Score) cho Vault Owner.
 */
@RestController
@RequestMapping("/api/owner/readiness")
@PreAuthorize("hasRole('OWNER')")
@RequiredArgsConstructor
public class OwnerReadinessController {

    private final OwnerReadinessService ownerReadinessService;

    @GetMapping
    public ResponseEntity<ApiResponse<ReadinessResponse>> getReadiness(Principal principal) {
        UUID ownerId = UUID.fromString(principal.getName());
        ReadinessResponse response = ownerReadinessService.evaluateReadiness(ownerId);
        return ResponseEntity.ok(ApiResponse.success("Estate readiness score retrieved successfully", response));
    }
}

