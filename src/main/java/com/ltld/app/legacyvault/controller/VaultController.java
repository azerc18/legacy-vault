package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.vaultdto.AdminVaultDetailResponse;
import com.ltld.app.legacyvault.dto.vaultdto.AssignBeneficiaryRequest;
import com.ltld.app.legacyvault.dto.vaultdto.AssignBeneficiaryResponse;
import com.ltld.app.legacyvault.dto.vaultdto.CreateVaultRequest;
import com.ltld.app.legacyvault.dto.vaultdto.UpdateVaultRequest;
import com.ltld.app.legacyvault.dto.vaultdto.VaultDetailResponse;
import com.ltld.app.legacyvault.dto.vaultdto.VaultResponse;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.service.vaultservice.VaultService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
                .ownerId(ownerId)
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

    // FR-27: Owner xem danh sách Vault của mình
    @GetMapping
    @PreAuthorize("hasAuthority('VAULT_READ')")
    public ResponseEntity<ApiResponse<List<VaultResponse>>> getMyVaults(Principal principal) {
        UUID ownerId = UUID.fromString(principal.getName());
        List<VaultResponse> list = vaultService.getMyVaults(ownerId);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách Vault thành công", list));
    }

    // FR-27: Owner xem chi tiết một Vault (trả 404 nếu không phải chủ sở hữu)
    @GetMapping("/{vaultId}")
    @PreAuthorize("hasAuthority('VAULT_READ')")
    public ResponseEntity<ApiResponse<VaultDetailResponse>> getVaultDetail(
            Principal principal,
            @PathVariable UUID vaultId) {
        UUID ownerId = UUID.fromString(principal.getName());
        VaultDetailResponse response = vaultService.getVaultDetail(vaultId, ownerId);
        return ResponseEntity.ok(ApiResponse.success("Lấy chi tiết Vault thành công", response));
    }

    // FR-27: Owner cập nhật tên/mô tả Vault (bị chặn khi DMS triggered hoặc có verification đang mở)
    @PutMapping("/{vaultId}")
    @PreAuthorize("hasAuthority('VAULT_CREATE')")
    public ResponseEntity<ApiResponse<VaultResponse>> updateVault(
            Principal principal,
            @PathVariable UUID vaultId,
            @Valid @RequestBody UpdateVaultRequest request) {
        UUID ownerId = UUID.fromString(principal.getName());
        VaultResponse response = vaultService.updateVault(vaultId, ownerId, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật Vault thành công", response));
    }

    // FR-20/Admin: Admin xem toàn bộ Vault, lọc theo trạng thái, phân trang
    @GetMapping("/admin/all")
    @PreAuthorize("hasAuthority('ADMIN_MANAGE')")
    public ResponseEntity<ApiResponse<Page<VaultResponse>>> getAllVaultsForAdmin(
            @RequestParam(required = false) VaultStatus status,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<VaultResponse> page = vaultService.getAllVaultsForAdmin(status, pageable);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách Vault thành công", page));
    }

    // FR-20/Admin: Admin xem chi tiết một Vault bất kỳ
    @GetMapping("/admin/{vaultId}")
    @PreAuthorize("hasAuthority('ADMIN_MANAGE')")
    public ResponseEntity<ApiResponse<AdminVaultDetailResponse>> getAdminVaultDetail(
            Principal principal,
            @PathVariable UUID vaultId) {
        UUID adminId = UUID.fromString(principal.getName());
        AdminVaultDetailResponse response = vaultService.getAdminVaultDetail(vaultId, adminId);
        return ResponseEntity.ok(ApiResponse.success("Lấy chi tiết Vault thành công", response));
    }
}
