package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.vaultdto.CreateVaultRequest;
import com.ltld.app.legacyvault.dto.vaultdto.VaultResponse;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.service.vaultservice.VaultService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
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
}

