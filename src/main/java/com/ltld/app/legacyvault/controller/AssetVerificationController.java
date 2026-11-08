package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.assetverificationdto.AssetVerificationResponse;
import com.ltld.app.legacyvault.dto.assetverificationdto.ConfirmVerificationRequest;
import com.ltld.app.legacyvault.dto.assetverificationdto.StartVerificationRequest;
import com.ltld.app.legacyvault.service.assetverificationservice.AssetVerificationService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

/**
 * C.5 – Owner xác thực tài sản.
 *
 * <pre>
 * GET  /api/vaults/{vaultId}/assets/{assetId}/verification           → trạng thái hiện tại
 * POST /api/vaults/{vaultId}/assets/{assetId}/verification/start     → bắt đầu một cách xác thực
 * POST /api/vaults/{vaultId}/assets/{assetId}/verification/confirm   → hoàn tất (mã, chữ ký, hash...)
 * </pre>
 */
@RestController
@RequestMapping("/api/vaults/{vaultId}/assets/{assetId}/verification")
@RequiredArgsConstructor
public class AssetVerificationController {

    private final AssetVerificationService assetVerificationService;

    @GetMapping
    @PreAuthorize("hasAuthority('VAULT_READ')")
    public ResponseEntity<ApiResponse<AssetVerificationResponse>> status(
            Principal principal, @PathVariable UUID vaultId, @PathVariable UUID assetId) {
        UUID ownerId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(ApiResponse.success("Verification status retrieved successfully",
                assetVerificationService.getStatus(ownerId, vaultId, assetId)));
    }

    @PostMapping("/start")
    @PreAuthorize("hasAuthority('VAULT_CREATE')")
    public ResponseEntity<ApiResponse<AssetVerificationResponse>> start(
            Principal principal, @PathVariable UUID vaultId, @PathVariable UUID assetId,
            @Valid @RequestBody StartVerificationRequest request) {
        UUID ownerId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(ApiResponse.success("Verification started",
                assetVerificationService.start(ownerId, vaultId, assetId, request)));
    }

    @PostMapping("/confirm")
    @PreAuthorize("hasAuthority('VAULT_CREATE')")
    public ResponseEntity<ApiResponse<AssetVerificationResponse>> confirm(
            Principal principal, @PathVariable UUID vaultId, @PathVariable UUID assetId,
            @Valid @RequestBody ConfirmVerificationRequest request) {
        UUID ownerId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(ApiResponse.success("Verification confirmed",
                assetVerificationService.confirm(ownerId, vaultId, assetId, request)));
    }
}
