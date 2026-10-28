package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.notificationdto.NotificationResponse;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.service.notificationservice.InAppNotificationService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

/** B7: thông báo trong app của người đang đăng nhập (mọi vai trò; chỉ cần đăng nhập, lọc theo người nhận). */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final InAppNotificationService inAppNotificationService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Page<NotificationResponse>>> listMine(
            Principal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("Notifications retrieved",
                        inAppNotificationService.listMine(currentUserId(principal), page, size)));
    }

    @GetMapping("/me/unread-count")
    public ResponseEntity<ApiResponse<Long>> unreadCount(Principal principal) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("Unread count retrieved",
                        inAppNotificationService.countUnread(currentUserId(principal))));
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<Void>> markRead(Principal principal, @PathVariable UUID notificationId) {
        inAppNotificationService.markRead(notificationId, currentUserId(principal));
        return ResponseEntity.ok(ApiResponse.success("Marked as read."));
    }

    private static UUID currentUserId(Principal principal) {
        try {
            return UUID.fromString(principal.getName());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessException("Invalid session.", HttpStatus.UNAUTHORIZED);
        }
    }
}
