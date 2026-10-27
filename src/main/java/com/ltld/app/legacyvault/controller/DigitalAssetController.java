package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.vaultdto.DigitalAssetDetailResponse;
import com.ltld.app.legacyvault.dto.vaultdto.DigitalAssetRequest;
import com.ltld.app.legacyvault.dto.vaultdto.DigitalAssetResponse;
import com.ltld.app.legacyvault.service.vaultservice.DigitalAssetService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

/**
 * FR-27 – Owner quản lý tài sản số.
 *
 * <pre>
 * GET    /api/vaults/{vaultId}/assets              → danh sách (không có secret)
 * GET    /api/vaults/{vaultId}/assets/{assetId}    → chi tiết (có secret đã giải mã)
 * POST   /api/vaults/{vaultId}/assets              → thêm mới
 * PUT    /api/vaults/{vaultId}/assets/{assetId}    → cập nhật
 * DELETE /api/vaults/{vaultId}/assets/{assetId}    → xóa cứng
 * </pre>
 *
 * Endpoint GET yêu cầu authority VAULT_READ; POST/PUT/DELETE yêu cầu authority VAULT_CREATE.
 * Kiểm tra ownership tại service layer; sai chủ → 404.
 */
@RestController
@RequestMapping("/api/vaults/{vaultId}/assets")
@RequiredArgsConstructor
public class DigitalAssetController {

    private final DigitalAssetService digitalAssetService;

    // FR-27: Danh sách tài sản ACTIVE (không trả secret)
    @GetMapping
    @PreAuthorize("hasAuthority('VAULT_READ')")
    public ResponseEntity<ApiResponse<List<DigitalAssetResponse>>> listAssets(
            Principal principal,
            @PathVariable UUID vaultId) {

        UUID ownerId = UUID.fromString(principal.getName());
        List<DigitalAssetResponse> list = digitalAssetService.listAssets(ownerId, vaultId);
        return ResponseEntity.ok(ApiResponse.success("Assets retrieved successfully", list));
    }

    // FR-27: Chi tiết 1 tài sản (kèm secret đã giải mã)
    @GetMapping("/{assetId}")
    @PreAuthorize("hasAuthority('VAULT_READ')")
    public ResponseEntity<ApiResponse<DigitalAssetDetailResponse>> getAsset(
            Principal principal,
            @PathVariable UUID vaultId,
            @PathVariable UUID assetId) throws Exception {

        UUID ownerId = UUID.fromString(principal.getName());
        DigitalAssetDetailResponse detail = digitalAssetService.getAsset(ownerId, vaultId, assetId);
        return ResponseEntity.ok(ApiResponse.success("Asset details retrieved successfully", detail));
    }

    // FR-27: Thêm tài sản mới
    @PostMapping
    @PreAuthorize("hasAuthority('VAULT_CREATE')")
    public ResponseEntity<ApiResponse<DigitalAssetResponse>> addAsset(
            Principal principal,
            @PathVariable UUID vaultId,
            @Valid @RequestBody DigitalAssetRequest request) throws Exception {

        UUID ownerId = UUID.fromString(principal.getName());
        DigitalAssetResponse response = digitalAssetService.addAsset(ownerId, vaultId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Asset added successfully", response));
    }

    // FR-27: Cập nhật tài sản
    @PutMapping("/{assetId}")
    @PreAuthorize("hasAuthority('VAULT_CREATE')")
    public ResponseEntity<ApiResponse<DigitalAssetResponse>> updateAsset(
            Principal principal,
            @PathVariable UUID vaultId,
            @PathVariable UUID assetId,
            @Valid @RequestBody DigitalAssetRequest request) throws Exception {

        UUID ownerId = UUID.fromString(principal.getName());
        DigitalAssetResponse response = digitalAssetService.updateAsset(ownerId, vaultId, assetId, request);
        return ResponseEntity.ok(ApiResponse.success("Asset updated successfully", response));
    }

    // FR-27: Xóa cứng tài sản
    @DeleteMapping("/{assetId}")
    @PreAuthorize("hasAuthority('VAULT_CREATE')")
    public ResponseEntity<ApiResponse<Void>> deleteAsset(
            Principal principal,
            @PathVariable UUID vaultId,
            @PathVariable UUID assetId) {

        UUID ownerId = UUID.fromString(principal.getName());
        digitalAssetService.deleteAsset(ownerId, vaultId, assetId);
        return ResponseEntity.ok(ApiResponse.success("Asset deleted successfully"));
    }
}
