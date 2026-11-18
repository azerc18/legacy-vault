package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.integrationdto.IntegrationsResponse;
import com.ltld.app.legacyvault.dto.integrationdto.UpdateKycMockRequest;
import com.ltld.app.legacyvault.service.integrationservice.IntegrationSettingsService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

/** Trang Tích hợp: trạng thái dịch vụ bên ngoài (chỉ đọc) và cờ eKYC mô phỏng. Không API nào nhận hay trả key. */
@RestController
@RequestMapping("/api/admin/integrations")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN_MANAGE')")
public class AdminIntegrationsController {

    private final IntegrationSettingsService integrationSettingsService;

    @GetMapping
    public ResponseEntity<ApiResponse<IntegrationsResponse>> get() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(integrationSettingsService.get()));
    }

    @PutMapping("/kyc-mock")
    public ResponseEntity<ApiResponse<IntegrationsResponse>> updateKycMock(Principal principal,
                                                                           @Valid @RequestBody UpdateKycMockRequest request) {
        UUID adminId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(ApiResponse.success("Integration settings saved",
                integrationSettingsService.updateKycMock(adminId, request)));
    }
}
