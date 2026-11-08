package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.sessiondto.UserSessionResponse;
import com.ltld.app.legacyvault.service.sessionservice.UserSessionService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

// Thiết bị đang đăng nhập (trang Hồ sơ).
// Đặt dưới /api/auth vì cookie refresh_token chỉ gửi kèm path /api/auth: nhờ đó BE biết thiết bị nào là "thiết bị này".
// SecurityConfig yêu cầu JWT cho /api/auth/sessions/** (khác các API /api/auth khác).
@RestController
@RequestMapping("/api/auth/sessions")
@RequiredArgsConstructor
public class UserSessionController {

    private final UserSessionService userSessionService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserSessionResponse>>> list(
            Principal principal,
            @CookieValue(name = "refresh_token", required = false) String refreshToken) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(userSessionService.listActive(currentUserId(principal), refreshToken)));
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<ApiResponse<Void>> revoke(Principal principal, @PathVariable UUID sessionId) {
        userSessionService.revoke(currentUserId(principal), sessionId);
        return ResponseEntity.ok(ApiResponse.success("Session signed out"));
    }

    @PostMapping("/revoke-others")
    public ResponseEntity<ApiResponse<Integer>> revokeOthers(
            Principal principal,
            @CookieValue(name = "refresh_token", required = false) String refreshToken) {
        int revoked = userSessionService.revokeOthers(currentUserId(principal), refreshToken);
        return ResponseEntity.ok(ApiResponse.success("Other sessions signed out", revoked));
    }

    private static UUID currentUserId(Principal principal) {
        return UUID.fromString(principal.getName());
    }
}
