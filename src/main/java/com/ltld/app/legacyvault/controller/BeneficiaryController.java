package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.beneficiarydto.AssetDownloadResponse;
import com.ltld.app.legacyvault.dto.beneficiarydto.BeneficiaryClaimRequest;
import com.ltld.app.legacyvault.dto.beneficiarydto.BeneficiaryClaimResponse;
import com.ltld.app.legacyvault.dto.beneficiarydto.BeneficiaryVaultSummaryResponse;
import com.ltld.app.legacyvault.dto.beneficiarydto.CloseVaultPreviewResponse;
import com.ltld.app.legacyvault.dto.beneficiarydto.CloseVaultRequest;
import com.ltld.app.legacyvault.dto.beneficiarydto.CloseVaultResponse;
import com.ltld.app.legacyvault.dto.beneficiarydto.DocumentDownloadResponse;
import com.ltld.app.legacyvault.dto.beneficiarydto.InheritedAssetDetailResponse;
import com.ltld.app.legacyvault.dto.beneficiarydto.InheritedAssetSummaryResponse;
import com.ltld.app.legacyvault.dto.beneficiarydto.LegalDocumentSummaryResponse;
import com.ltld.app.legacyvault.dto.beneficiarydto.VerifyIdentityRequest;
import com.ltld.app.legacyvault.dto.beneficiarydto.VerifyIdentityResponse;
import com.ltld.app.legacyvault.exception.BeneficiaryException;
import com.ltld.app.legacyvault.service.beneficiaryservice.BeneficiaryService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @PostMapping("/claim")
    public ResponseEntity<ApiResponse<BeneficiaryClaimResponse>> initializeClaim(
            Principal principal,
            @Valid @RequestBody BeneficiaryClaimRequest request) {

        BeneficiaryClaimResponse response =
                beneficiaryService.initializeClaim(request, currentUserId(principal));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/vaults")
    public ResponseEntity<ApiResponse<List<BeneficiaryVaultSummaryResponse>>> listMyVaults(Principal principal) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(beneficiaryService.listMyVaults(currentUserId(principal))));
    }

    @PostMapping("/verifications/otp")
    public ResponseEntity<ApiResponse<Void>> sendIdentityOtp(
            Principal principal,
            @Valid @RequestBody BeneficiaryClaimRequest request) {

        beneficiaryService.sendIdentityOtp(request, currentUserId(principal));
        return ResponseEntity.ok(ApiResponse.success("OTP đã được gửi đến email của bạn."));
    }

    @PostMapping("/verifications/verify")
    public ResponseEntity<ApiResponse<VerifyIdentityResponse>> verifyIdentity(
            Principal principal,
            @Valid @RequestBody VerifyIdentityRequest request) {

        VerifyIdentityResponse response = beneficiaryService.verifyIdentity(request, currentUserId(principal));
        return ResponseEntity.ok(ApiResponse.success("Xác thực danh tính thành công.", response));
    }

    @GetMapping("/vaults/{vaultId}/assets")
    public ResponseEntity<ApiResponse<List<InheritedAssetSummaryResponse>>> getInheritedAssets(
            Principal principal,
            @PathVariable UUID vaultId) {

        List<InheritedAssetSummaryResponse> assets =
                beneficiaryService.getInheritedAssets(vaultId, currentUserId(principal));
        return ResponseEntity.ok(ApiResponse.success(assets));
    }

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

    @GetMapping("/vaults/{vaultId}/close-preview")
    public ResponseEntity<ApiResponse<CloseVaultPreviewResponse>> previewClose(
            Principal principal,
            @PathVariable UUID vaultId) {

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(beneficiaryService.previewClose(vaultId, currentUserId(principal))));
    }

    @PostMapping("/vaults/{vaultId}/close")
    public ResponseEntity<ApiResponse<CloseVaultResponse>> closeVault(
            Principal principal,
            @PathVariable UUID vaultId,
            @Valid @RequestBody CloseVaultRequest request) {

        CloseVaultResponse response = beneficiaryService.closeVault(vaultId, request, currentUserId(principal));
        return ResponseEntity.ok(ApiResponse.success("Đã đóng hồ sơ nhận bàn giao.", response));
    }

    private UUID currentUserId(Principal principal) {
        try {
            return UUID.fromString(principal.getName());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BeneficiaryException("Phiên đăng nhập không hợp lệ.", HttpStatus.UNAUTHORIZED);
        }
    }

    @PreAuthorize("hasRole('BENEFICIARY') and hasAuthority('ASSET_DOWNLOAD')")
    @GetMapping("/vaults/{vaultId}/documents")
    public ResponseEntity<ApiResponse<List<LegalDocumentSummaryResponse>>> getVaultDocuments(
            Principal principal,
            @PathVariable UUID vaultId) {

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(beneficiaryService.getVaultDocuments(vaultId, currentUserId(principal))));
    }

    @PreAuthorize("hasRole('BENEFICIARY') and hasAuthority('ASSET_DOWNLOAD')")
    @GetMapping("/vaults/{vaultId}/documents/{documentId}/download")
    public ResponseEntity<byte[]> downloadVaultDocument(
            Principal principal,
            @PathVariable UUID vaultId,
            @PathVariable UUID documentId) {

        DocumentDownloadResponse file =
                beneficiaryService.downloadVaultDocument(vaultId, documentId, currentUserId(principal));

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.getFileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(file.getContent());
    }
}