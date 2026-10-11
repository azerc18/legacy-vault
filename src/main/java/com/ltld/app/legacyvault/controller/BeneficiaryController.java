package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.beneficiarydto.*;
import com.ltld.app.legacyvault.exception.BeneficiaryException;
import com.ltld.app.legacyvault.service.beneficiaryservice.BeneficiaryService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/beneficiaries")
@PreAuthorize("hasRole('BENEFICIARY')")
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
    @PreAuthorize("hasRole('BENEFICIARY') and hasAuthority('ASSET_DOWNLOAD')")
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

    // FR-18: Tải xuống thông tin tài sản đã giải mã dưới dạng tệp. Không cho cache dữ liệu nhạy cảm.
    @PreAuthorize("hasRole('BENEFICIARY') and hasAuthority('ASSET_DOWNLOAD')")
    @GetMapping(value = "/vaults/{vaultId}/assets/{assetId}/download",
            produces = {MediaType.TEXT_PLAIN_VALUE, MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<byte[]> downloadInheritedAsset(
            Principal principal,
            @PathVariable UUID vaultId,
            @PathVariable UUID assetId) {

        AssetDownloadResponse file =
                beneficiaryService.downloadInheritedAsset(vaultId, assetId, currentUserId(principal));

        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.getFileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(file.getContent());
    }

    // FR-19: Beneficiary xác nhận đã nhận bàn giao để đóng hồ sơ Vault
    @PostMapping("/vaults/{vaultId}/close")
    public ResponseEntity<ApiResponse<CloseVaultResponse>> closeVault(
            Principal principal,
            @PathVariable UUID vaultId,
            @Valid @RequestBody CloseVaultRequest request) {

        CloseVaultResponse response = beneficiaryService.closeVault(vaultId, request, currentUserId(principal));
        return ResponseEntity.ok(ApiResponse.success("Đã đóng hồ sơ nhận bàn giao.", response));
    }

    // Lấy UUID người dùng từ JWT, cùng cách với VaultController
    private UUID currentUserId(Principal principal) {
        try {
            return UUID.fromString(principal.getName());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BeneficiaryException("Phiên đăng nhập không hợp lệ.", HttpStatus.UNAUTHORIZED);
        }
    }
}