package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.beneficiarydto.BeneficiaryClaimRequest;
import com.ltld.app.legacyvault.dto.beneficiarydto.BeneficiaryClaimResponse;
import com.ltld.app.legacyvault.dto.beneficiarydto.InheritedAssetDetailResponse;
import com.ltld.app.legacyvault.dto.beneficiarydto.InheritedAssetSummaryResponse;
import com.ltld.app.legacyvault.dto.beneficiarydto.VerifyIdentityRequest;
import com.ltld.app.legacyvault.dto.beneficiarydto.VerifyIdentityResponse;
import com.ltld.app.legacyvault.service.beneficiaryservice.BeneficiaryService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/beneficiaries")
@RequiredArgsConstructor
public class BeneficiaryController {

    private final BeneficiaryService beneficiaryService;

    // FR-16: Khởi tạo yêu cầu nhận tài sản
    @PostMapping("/claim")
    public ResponseEntity<ApiResponse<BeneficiaryClaimResponse>> initializeClaim(
            Principal principal,
            @Valid @RequestBody BeneficiaryClaimRequest request) {

        BeneficiaryClaimResponse response =
                beneficiaryService.initializeClaim(request, currentUserId(principal));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // FR-17: Gửi OTP xác thực danh tính qua email
    @PostMapping("/verifications/otp")
    public ResponseEntity<ApiResponse<Void>> sendIdentityOtp(
            Principal principal,
            @Valid @RequestBody BeneficiaryClaimRequest request) {

        beneficiaryService.sendIdentityOtp(request, currentUserId(principal));
        return ResponseEntity.ok(ApiResponse.success("OTP đã được gửi đến email của bạn."));
    }

    // FR-17: Xác thực danh tính bằng OTP hoặc mock eKYC
    @PostMapping("/verifications/verify")
    public ResponseEntity<ApiResponse<VerifyIdentityResponse>> verifyIdentity(
            Principal principal,
            @Valid @RequestBody VerifyIdentityRequest request) {

        VerifyIdentityResponse response = beneficiaryService.verifyIdentity(request, currentUserId(principal));
        return ResponseEntity.ok(ApiResponse.success("Xác thực danh tính thành công.", response));
    }

    // FR-17: Danh sách tài sản trong Vault (sau khi xác thực)
    @GetMapping("/vaults/{vaultId}/assets")
    public ResponseEntity<ApiResponse<List<InheritedAssetSummaryResponse>>> getInheritedAssets(
            Principal principal,
            @PathVariable UUID vaultId) {

        List<InheritedAssetSummaryResponse> assets =
                beneficiaryService.getInheritedAssets(vaultId, currentUserId(principal));
        return ResponseEntity.ok(ApiResponse.success(assets));
    }

    // FR-17: Chi tiết một tài sản đã giải mã tạm thời. Không cho trình duyệt/proxy cache dữ liệu nhạy cảm
    @GetMapping("/vaults/{vaultId}/assets/{assetId}")
    public ResponseEntity<ApiResponse<InheritedAssetDetailResponse>> getInheritedAssetDetail(
            Principal principal,
            @PathVariable UUID vaultId,
            @PathVariable UUID assetId) {

        InheritedAssetDetailResponse detail =
                beneficiaryService.getInheritedAssetDetail(vaultId, assetId, currentUserId(principal));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(detail));
    }

    // Lấy UUID người dùng từ JWT, cùng cách với VaultController
    private UUID currentUserId(Principal principal) {
        return UUID.fromString(principal.getName());
    }
}