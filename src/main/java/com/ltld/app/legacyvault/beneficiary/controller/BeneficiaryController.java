package com.ltld.app.legacyvault.beneficiary.controller;

import com.ltld.app.legacyvault.beneficiary.dto.*;
import com.ltld.app.legacyvault.beneficiary.service.BeneficiaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/beneficiaries")
@RequiredArgsConstructor
public class BeneficiaryController {

    private final BeneficiaryService beneficiaryService;

    // FR-16: Khởi tạo yêu cầu nhận tài sản
    @PostMapping("/claim")
    public ResponseEntity<BeneficiaryClaimResponse> processClaim(@Valid @RequestBody BeneficiaryClaimRequest request) {
        return ResponseEntity.ok(beneficiaryService.processClaim(request));
    }

    // FR-17 & FR-18: Gửi mã OTP/KYC để xác thực và nhận link tải tài sản
    @PostMapping("/verify")
    public ResponseEntity<AssetViewResponse> verifyIdentity(@Valid @RequestBody VerifyIdentityRequest request) {
        return ResponseEntity.ok(beneficiaryService.verifyIdentityAndViewAsset(request));
    }

    // FR-18: Tải xuống tài sản đã giải mã
    @GetMapping("/download/{vaultId}")
    public ResponseEntity<String> downloadAsset(@PathVariable UUID vaultId) {
        // Trong thực tế, hàm này sẽ đọc file từ AWS S3/MinIO, giải mã và trả về stream file (PDF, MP4...)
        // Hiện tại ta mock trả về một chuỗi đại diện cho file
        return ResponseEntity.ok("BẮT ĐẦU TẢI FILE: File_Tai_San_Bi_Mat_Cua_Vault_" + vaultId + ".zip");
    }

    // FR-19: Xác nhận đóng hồ sơ (Truyền vaultId trực tiếp trên URL)
    @PostMapping("/confirm/{vaultId}")
    public ResponseEntity<String> confirmClaimClosing(@PathVariable UUID vaultId) {
        beneficiaryService.confirmClaimClosing(vaultId);
        return ResponseEntity.ok("Hồ sơ đã được xác nhận đóng thành công.");
    }
}