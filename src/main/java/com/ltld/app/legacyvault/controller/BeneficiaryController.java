package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.beneficiarydto.AcceptClaimResponse;
import com.ltld.app.legacyvault.dto.beneficiarydto.AssetDownloadResponse;
import com.ltld.app.legacyvault.dto.beneficiarydto.BeneficiaryClaimRequest;
import com.ltld.app.legacyvault.dto.beneficiarydto.BeneficiaryClaimResponse;
import com.ltld.app.legacyvault.dto.beneficiarydto.BeneficiaryNotificationResponse;
import com.ltld.app.legacyvault.dto.beneficiarydto.BeneficiaryVaultSummaryResponse;
import com.ltld.app.legacyvault.dto.beneficiarydto.CloseVaultPreviewResponse;
import com.ltld.app.legacyvault.dto.beneficiarydto.CloseVaultRequest;
import com.ltld.app.legacyvault.dto.beneficiarydto.CloseVaultResponse;
import com.ltld.app.legacyvault.dto.beneficiarydto.DeclineClaimRequest;
import com.ltld.app.legacyvault.dto.beneficiarydto.DeclineClaimResponse;
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
import org.springframework.web.bind.annotation.PatchMapping;
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

    // FR-16: Khởi tạo yêu cầu nhận tài sản
    @PostMapping("/claim")
    public ResponseEntity<ApiResponse<BeneficiaryClaimResponse>> initializeClaim(
            Principal principal,
            @Valid @RequestBody BeneficiaryClaimRequest request) {

        BeneficiaryClaimResponse response =
                beneficiaryService.initializeClaim(request, currentUserId(principal));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // FR-16/FR-17 bước 1: danh sách vault được bàn giao cho Beneficiary đang đăng nhập
    @GetMapping("/vaults")
    public ResponseEntity<ApiResponse<List<BeneficiaryVaultSummaryResponse>>> listMyVaults(Principal principal) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(beneficiaryService.listMyVaults(currentUserId(principal))));
    }

    // FR-16: thông báo trong hệ thống
    @GetMapping("/notifications")
    public ResponseEntity<ApiResponse<List<BeneficiaryNotificationResponse>>> listMyNotifications(Principal principal) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(beneficiaryService.listMyNotifications(currentUserId(principal))));
    }

    // FR-16: đánh dấu thông báo đã đọc
    @PatchMapping("/notifications/{notificationId}/read")
    public ResponseEntity<ApiResponse<Void>> markNotificationRead(
            Principal principal, @PathVariable UUID notificationId) {
        beneficiaryService.markNotificationRead(notificationId, currentUserId(principal));
        return ResponseEntity.ok(ApiResponse.success("Marked as read."));
    }

    // FR-17: Gửi OTP xác thực danh tính qua email
    @PostMapping("/verifications/otp")
    public ResponseEntity<ApiResponse<Void>> sendIdentityOtp(
            Principal principal,
            @Valid @RequestBody BeneficiaryClaimRequest request) {

        beneficiaryService.sendIdentityOtp(request, currentUserId(principal));
        return ResponseEntity.ok(ApiResponse.success("An OTP has been sent to your email."));
    }

    // FR-17: Xác thực danh tính bằng OTP hoặc mock eKYC
    @PostMapping("/verifications/verify")
    public ResponseEntity<ApiResponse<VerifyIdentityResponse>> verifyIdentity(
            Principal principal,
            @Valid @RequestBody VerifyIdentityRequest request) {

        VerifyIdentityResponse response = beneficiaryService.verifyIdentity(request, currentUserId(principal));
        return ResponseEntity.ok(ApiResponse.success("Identity verified successfully.", response));
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

    // FR-19: Xem trước khi đóng hồ sơ (tài sản đã/chưa xem), không giải mã gì
    @GetMapping("/vaults/{vaultId}/close-preview")
    public ResponseEntity<ApiResponse<CloseVaultPreviewResponse>> previewClose(
            Principal principal,
            @PathVariable UUID vaultId) {

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(beneficiaryService.previewClose(vaultId, currentUserId(principal))));
    }

    // FR-19: Beneficiary xác nhận đã nhận bàn giao để đóng hồ sơ Vault
    @PostMapping("/vaults/{vaultId}/close")
    public ResponseEntity<ApiResponse<CloseVaultResponse>> closeVault(
            Principal principal,
            @PathVariable UUID vaultId,
            @Valid @RequestBody CloseVaultRequest request) {

        CloseVaultResponse response = beneficiaryService.closeVault(vaultId, request, currentUserId(principal));
        return ResponseEntity.ok(ApiResponse.success("Handover case closed.", response));
    }

    // N2b: Beneficiary từ chối phần được chia; tài sản chuyển sang người dự phòng (nếu có)
    // N2b: Beneficiary đã xác minh danh tính xác nhận nhận phần được chia
    @PostMapping("/vaults/{vaultId}/accept")
    public ResponseEntity<ApiResponse<AcceptClaimResponse>> acceptClaim(
            Principal principal,
            @PathVariable UUID vaultId) {

        AcceptClaimResponse response = beneficiaryService.acceptClaim(vaultId, currentUserId(principal));
        return ResponseEntity.ok(ApiResponse.success("Inheritance accepted.", response));
    }

    // N2b: Beneficiary từ chối phần được chia; tài sản chuyển sang người dự phòng (nếu có)
    @PostMapping("/vaults/{vaultId}/decline")
    public ResponseEntity<ApiResponse<DeclineClaimResponse>> declineClaim(
            Principal principal,
            @PathVariable UUID vaultId,
            @Valid @RequestBody(required = false) DeclineClaimRequest request) {

        String reason = request != null ? request.getReason() : null;
        DeclineClaimResponse response = beneficiaryService.declineClaim(vaultId, currentUserId(principal), reason);
        return ResponseEntity.ok(ApiResponse.success("Inheritance declined.", response));
    }

    // Lấy UUID người dùng từ JWT, cùng cách với VaultController
    private UUID currentUserId(Principal principal) {
        try {
            return UUID.fromString(principal.getName());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BeneficiaryException("Invalid session.", HttpStatus.UNAUTHORIZED);
        }
    }

    // FR-18 (phần 2): Danh sách tài liệu pháp lý của vault (chỉ thông tin tóm tắt)
    @PreAuthorize("hasRole('BENEFICIARY') and hasAuthority('ASSET_DOWNLOAD')")
    @GetMapping("/vaults/{vaultId}/documents")
    public ResponseEntity<ApiResponse<List<LegalDocumentSummaryResponse>>> getVaultDocuments(
            Principal principal,
            @PathVariable UUID vaultId) {

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(beneficiaryService.getVaultDocuments(vaultId, currentUserId(principal))));
    }

    // FR-18 (phần 2): Tải xuống tài liệu pháp lý đã giải mã. Không cho cache dữ liệu nhạy cảm.
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