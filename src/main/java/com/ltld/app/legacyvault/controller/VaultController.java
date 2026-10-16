package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.vaultdto.*;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.service.vaultservice.VaultService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/vaults") // URL chung: /api/vaults
@RequiredArgsConstructor
public class VaultController {

    private final VaultService vaultService;

    // Khi Client gọi POST /api/vaults, hàm này chạy
    @PostMapping
    @PreAuthorize("hasAuthority('VAULT_CREATE')")
    public ResponseEntity<ApiResponse<VaultResponse>> createVault(
            Principal principal,
            @RequestBody CreateVaultRequest request) throws Exception {

        // Lấy UUID của người dùng đang đăng nhập (chứng thực qua JWT)
        UUID ownerId = UUID.fromString(principal.getName());
        Vault vault = vaultService.createVault(ownerId, request);

        VaultResponse response = VaultResponse.builder()
                .id(vault.getId())
                .name(vault.getName())
                .description(vault.getDescription())
                .status(vault.getStatus())
                .createdAt(vault.getCreatedAt())
                .updatedAt(vault.getUpdatedAt())
                .build();

        return ResponseEntity.ok(ApiResponse.success("Vault created successfully", response));
    }

    // Khi Client gọi DELETE /api/vaults/abcd-1234, hàm này chạy
    @DeleteMapping("/{vaultId}")
    @PreAuthorize("hasAuthority('VAULT_DELETE')")
    public ResponseEntity<ApiResponse<Void>> deleteVault(
            Principal principal,
            @PathVariable UUID vaultId) throws Exception {

        UUID ownerId = UUID.fromString(principal.getName());
        vaultService.deleteVault(vaultId, ownerId);
        return ResponseEntity.ok(ApiResponse.success("Vault deleted successfully"));
    }

    // FR-02: Owner chỉ định (hoặc thay thế) người thụ hưởng cho Vault
    @PutMapping("/{vaultId}/beneficiary")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<AssignBeneficiaryResponse>> assignBeneficiary(
            Principal principal,
            @PathVariable UUID vaultId,
            @Valid @RequestBody AssignBeneficiaryRequest request) {

        UUID ownerId = UUID.fromString(principal.getName());
        AssignBeneficiaryResponse response = vaultService.assignBeneficiary(ownerId, vaultId, request);
        return ResponseEntity.ok(ApiResponse.success("Chỉ định người thụ hưởng thành công", response));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('VAULT_READ')")
    public ResponseEntity<ApiResponse<List<VaultResponse>>> getMyVaults(Principal principal) {
        UUID ownerId = UUID.fromString(principal.getName());
        List<VaultResponse> list = vaultService.getMyVaults(ownerId);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách Vault thành công", list));
    }

    @GetMapping("/{vaultId}")
    @PreAuthorize("hasAuthority('VAULT_READ')")
    public ResponseEntity<ApiResponse<VaultDetailResponse>> getVaultDetail(
            Principal principal,
            @PathVariable UUID vaultId,
            org.springframework.security.core.Authentication authentication) {
        UUID requesterId = UUID.fromString(principal.getName());
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ADMIN_MANAGE") || a.getAuthority().equals("ROLE_ADMIN"));

        VaultDetailResponse response = vaultService.getVaultDetail(vaultId, requesterId, isAdmin);
        return ResponseEntity.ok(ApiResponse.success("Lấy chi tiết Vault thành công", response));
    }

    @PutMapping("/{vaultId}")
    @PreAuthorize("hasAuthority('VAULT_CREATE')")
    public ResponseEntity<ApiResponse<VaultResponse>> updateVault(
            Principal principal,
            @PathVariable UUID vaultId,
            @jakarta.validation.Valid @RequestBody UpdateVaultRequest request) {
        UUID ownerId = UUID.fromString(principal.getName());
        VaultResponse response = vaultService.updateVault(vaultId, ownerId, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật Vault thành công", response));
    }

    @GetMapping("/admin/all")
    @PreAuthorize("hasAuthority('ADMIN_MANAGE')")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<VaultResponse>>> getAllVaultsAdmin(
            @RequestParam(required = false) com.ltld.app.legacyvault.enums.VaultStatus status,
            org.springframework.data.domain.Pageable pageable) {
        org.springframework.data.domain.Page<VaultResponse> page = vaultService.getAllVaultsForAdmin(status, pageable);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách Vault cho Admin thành công", page));
    }

}

