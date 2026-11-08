package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.beneficiarydto.BeneficiaryLockResponse;
import com.ltld.app.legacyvault.dto.beneficiarydto.UnlockBeneficiaryRequest;
import com.ltld.app.legacyvault.service.beneficiaryservice.BeneficiaryLockAdminService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

/** #10: Admin xem / mở khóa người thụ hưởng bị khóa xác minh danh tính. */
@RestController
@RequestMapping("/api/admin/beneficiary-locks")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN_MANAGE')")
public class AdminBeneficiaryLockController {

    private final BeneficiaryLockAdminService beneficiaryLockAdminService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<BeneficiaryLockResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(beneficiaryLockAdminService.listActiveLocks()));
    }

    @PostMapping("/unlock")
    public ResponseEntity<ApiResponse<Void>> unlock(Principal principal,
                                                    @Valid @RequestBody UnlockBeneficiaryRequest request) {
        UUID adminId = UUID.fromString(principal.getName());
        beneficiaryLockAdminService.unlock(adminId, request.getVaultId(), request.getBeneficiaryId(), request.getReason());
        return ResponseEntity.ok(ApiResponse.success("Beneficiary unlocked", null));
    }
}
