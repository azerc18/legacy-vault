package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.vaultdto.AssignBeneficiaryRequest;
import com.ltld.app.legacyvault.dto.vaultdto.AssignBeneficiaryResponse;
import com.ltld.app.legacyvault.dto.vaultdto.CreateVaultRequest;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.service.vaultservice.VaultService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    public ResponseEntity<ApiResponse<Vault>> createVault(
            Principal principal,
            @RequestBody CreateVaultRequest request) throws Exception {

        // Lấy UUID của người dùng đang đăng nhập (chứng thực qua JWT)
        UUID ownerId = UUID.fromString(principal.getName());
        Vault vault = vaultService.createVault(ownerId, request);
        return ResponseEntity.ok(ApiResponse.success("Vault created successfully", vault));
    }

    // Khi Client gọi DELETE /api/vaults/abcd-1234, hàm này chạy
    @DeleteMapping("/{vaultId}")
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
}

