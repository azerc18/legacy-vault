package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.identitydto.*;
import com.ltld.app.legacyvault.service.identityservice.OwnerIdentityService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.UUID;

// Xác minh danh tính Owner (KYC BASIC), điều kiện để bật Dead Man's Switch lần đầu.
@RestController
@RequestMapping("/api/owner/identity")
@PreAuthorize("hasRole('OWNER')")
@RequiredArgsConstructor
public class OwnerIdentityController {

    private final OwnerIdentityService identityService;

    @GetMapping
    public ResponseEntity<ApiResponse<IdentityStatusResponse>> getStatus(Principal principal) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(identityService.getStatus(userId(principal))));
    }

    @PostMapping("/extract/qr")
    public ResponseEntity<ApiResponse<IdentityExtractResponse>> extractQr(
            Principal principal, @Valid @RequestBody QrExtractRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(identityService.extractFromQr(userId(principal), request.getQrText())));
    }

    @PostMapping(value = "/extract/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<IdentityExtractResponse>> extractImage(
            Principal principal,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "aiOcrConsent", defaultValue = "false") boolean aiOcrConsent) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(identityService.extractFromImage(userId(principal), file, aiOcrConsent)));
    }

    @PostMapping("/phone/otp")
    public ResponseEntity<ApiResponse<PhoneOtpResponse>> sendPhoneOtp(
            Principal principal, @Valid @RequestBody PhoneOtpRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Verification code sent",
                identityService.sendPhoneOtp(userId(principal), request.getPhoneNumber())));
    }

    @PostMapping("/phone/verify")
    public ResponseEntity<ApiResponse<IdentityStatusResponse>> verifyPhone(
            Principal principal, @Valid @RequestBody PhoneVerifyRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Phone number verified",
                identityService.verifyPhoneOtp(userId(principal), request.getCode())));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<IdentityStatusResponse>> submit(
            Principal principal, @Valid @RequestBody SubmitIdentityRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Identity verified",
                identityService.submit(userId(principal), request)));
    }

    private static UUID userId(Principal principal) {
        return UUID.fromString(principal.getName());
    }
}
