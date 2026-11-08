package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.accountdto.AccountDeletionCheckResponse;
import com.ltld.app.legacyvault.dto.accountdto.DeleteAccountRequest;
import com.ltld.app.legacyvault.service.accountservice.AccountDeletionService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

// Trang Hồ sơ > Xoá tài khoản
@RestController
@RequestMapping("/api/users/me/account")
@RequiredArgsConstructor
public class AccountDeletionController {

    private final AccountDeletionService accountDeletionService;

    @GetMapping("/deletion-check")
    public ResponseEntity<ApiResponse<AccountDeletionCheckResponse>> check(Principal principal) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(accountDeletionService.check(currentUserId(principal))));
    }

    @PostMapping("/delete")
    public ResponseEntity<ApiResponse<Void>> delete(Principal principal, @Valid @RequestBody DeleteAccountRequest request) {
        accountDeletionService.delete(currentUserId(principal), request);
        return ResponseEntity.ok(ApiResponse.success("Account deleted"));
    }

    private static UUID currentUserId(Principal principal) {
        return UUID.fromString(principal.getName());
    }
}
