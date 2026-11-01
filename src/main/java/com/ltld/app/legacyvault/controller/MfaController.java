package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.mfadto.MfaBackupCodesResponse;
import com.ltld.app.legacyvault.dto.mfadto.MfaCodeRequest;
import com.ltld.app.legacyvault.dto.mfadto.MfaSetupResponse;
import com.ltld.app.legacyvault.service.mfaservice.MfaService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

// H4: bật/tắt 2FA TOTP cho người dùng đang đăng nhập
@RestController
@RequestMapping("/api/users/me/2fa")
@RequiredArgsConstructor
public class MfaController {

    private final MfaService mfaService;

    @PostMapping("/setup")
    public ResponseEntity<ApiResponse<MfaSetupResponse>> setup(Principal principal) {
        // Có secret trong body -> không cho trình duyệt/proxy cache
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("Scan the QR code, then enter the 6-digit code",
                        mfaService.setup(currentUserId(principal))));
    }

    @PostMapping("/enable")
    public ResponseEntity<ApiResponse<MfaBackupCodesResponse>> enable(
            Principal principal, @Valid @RequestBody MfaCodeRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("Two-factor authentication enabled",
                        new MfaBackupCodesResponse(mfaService.enable(currentUserId(principal), request.code()))));
    }

    @PostMapping("/disable")
    public ResponseEntity<ApiResponse<Void>> disable(
            Principal principal, @Valid @RequestBody MfaCodeRequest request) {
        mfaService.disable(currentUserId(principal), request.code());
        return ResponseEntity.ok(ApiResponse.success("Two-factor authentication disabled"));
    }

    private static UUID currentUserId(Principal principal) {
        return UUID.fromString(principal.getName());
    }
}