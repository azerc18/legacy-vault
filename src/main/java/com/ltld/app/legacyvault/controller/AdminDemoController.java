package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.adminuserdto.DemoToolsResponse;
import com.ltld.app.legacyvault.dto.dmsdto.DmsConfigResponse;
import com.ltld.app.legacyvault.exception.DmsException;
import com.ltld.app.legacyvault.service.dmsservice.DmsConfigService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

/**
 * Công cụ CHỈ dùng khi trình diễn. Tắt mặc định (app.demo.tools-enabled=false): GET /tools trả false để trang Admin
 * ẩn nút, mọi thao tác trả 404 như không tồn tại. Bật bằng biến môi trường APP_DEMO_TOOLS_ENABLED=true.
 */
@RestController
@RequestMapping("/api/admin/demo")
@PreAuthorize("hasAuthority('ADMIN_MANAGE')")
public class AdminDemoController {
    static final String DISABLED_MESSAGE = "Demo tools are disabled.";

    private final DmsConfigService dmsConfigService;
    private final boolean enabled;

    public AdminDemoController(DmsConfigService dmsConfigService,
                               @Value("${app.demo.tools-enabled:false}") boolean enabled) {
        this.dmsConfigService = dmsConfigService;
        this.enabled = enabled;
    }

    @GetMapping("/tools")
    public ResponseEntity<ApiResponse<DemoToolsResponse>> tools() {
        return ResponseEntity.ok(ApiResponse.success("Demo tools", new DemoToolsResponse(enabled)));
    }

    // Giả Owner quá hạn check-in; job DMS (khoảng 20 giây) chạy tiếp luồng thật.
    @PostMapping("/dms/{ownerId}/simulate-overdue")
    public ResponseEntity<ApiResponse<DmsConfigResponse>> simulateOverdue(Principal principal,
                                                                          @PathVariable UUID ownerId) {
        if (!enabled) {
            throw new DmsException(DISABLED_MESSAGE, HttpStatus.NOT_FOUND);
        }
        UUID adminId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(ApiResponse.success("Owner marked overdue; the DMS job will process it shortly",
                dmsConfigService.simulateOverdueByAdmin(ownerId, adminId)));
    }
}
