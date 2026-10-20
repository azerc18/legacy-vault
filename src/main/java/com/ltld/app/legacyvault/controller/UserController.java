package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.userdto.AvatarFile;
import com.ltld.app.legacyvault.dto.userdto.ChangePasswordRequest;
import com.ltld.app.legacyvault.dto.userdto.UpdateProfileRequest;
import com.ltld.app.legacyvault.dto.userdto.UserProfileResponse;
import com.ltld.app.legacyvault.service.userservice.UserProfileService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    // ---------- Avatar ----------

    @PutMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateAvatar(
            Principal principal, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.success("Cập nhật ảnh đại diện thành công",
                userProfileService.updateAvatar(currentUserId(principal), file)));
    }

    // Trả về chính file ảnh (không bọc ApiResponse) để FE hiển thị
    @GetMapping("/avatar")
    public ResponseEntity<byte[]> getAvatar(Principal principal) {
        AvatarFile avatar = userProfileService.getAvatar(currentUserId(principal));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(avatar.contentType()))
                .header("X-Content-Type-Options", "nosniff")
                .body(avatar.content());
    }

    @DeleteMapping("/avatar")
    public ResponseEntity<ApiResponse<UserProfileResponse>> deleteAvatar(Principal principal) {
        return ResponseEntity.ok(ApiResponse.success("Đã xoá ảnh đại diện",
                userProfileService.deleteAvatar(currentUserId(principal))));
    }

    private static UUID currentUserId(Principal principal) {
        return UUID.fromString(principal.getName());
    }
}
