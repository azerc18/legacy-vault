package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.adminoverviewdto.AdminNotificationResponse;
import com.ltld.app.legacyvault.enums.NotificationStatus;
import com.ltld.app.legacyvault.service.adminoverviewservice.AdminNotificationService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

/** B1: email thông báo gửi lỗi + gửi lại thủ công. */
@RestController
@RequestMapping("/api/admin/notifications")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN_MANAGE')")
public class AdminNotificationController {

    private final AdminNotificationService adminNotificationService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<AdminNotificationResponse>>> list(
            @RequestParam(defaultValue = "FAILED") NotificationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(adminNotificationService.list(status, page, size)));
    }

    @PostMapping("/{notificationId}/retry")
    public ResponseEntity<ApiResponse<AdminNotificationResponse>> retry(Principal principal,
                                                                        @PathVariable UUID notificationId) {
        UUID adminId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(ApiResponse.success("Email resent",
                adminNotificationService.retry(adminId, notificationId)));
    }
}
