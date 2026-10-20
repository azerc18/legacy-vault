package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.userdto.ChangePasswordRequest;
import com.ltld.app.legacyvault.dto.userdto.UpdateProfileRequest;
import com.ltld.app.legacyvault.dto.userdto.UserProfileResponse;
import com.ltld.app.legacyvault.service.userservice.UserProfileService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserProfileService userProfileService;

    @GetMapping
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMe(Principal principal) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(userProfileService.getMe(currentUserId(principal))));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateMe(
            Principal principal, @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Cập nhật hồ sơ thành công",
                userProfileService.updateMe(currentUserId(principal), request)));
    }

    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            Principal principal, @Valid @RequestBody ChangePasswordRequest request) {
        userProfileService.changePassword(currentUserId(principal), request);
        return ResponseEntity.ok(ApiResponse.success("Đổi mật khẩu thành công. Vui lòng đăng nhập lại."));
    }

    private static UUID currentUserId(Principal principal) {
        return UUID.fromString(principal.getName());
    }
}