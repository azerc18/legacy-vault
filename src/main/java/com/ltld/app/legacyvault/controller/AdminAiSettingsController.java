package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.aisettingsdto.AiFeatureSettingResponse;
import com.ltld.app.legacyvault.dto.aisettingsdto.AiSettingsResponse;
import com.ltld.app.legacyvault.dto.aisettingsdto.UpdateAiFeatureSettingRequest;
import com.ltld.app.legacyvault.utility.ApiResponse;
import com.ltld.app.legacyvault.enums.AiFeature;
import com.ltld.app.legacyvault.service.aisettingsservice.AiSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

/** Admin bật / tắt chức năng AI và chọn model. Không có API nào nhận hay trả API key. */
@RestController
@RequestMapping("/api/admin/ai-settings")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN_MANAGE')")
public class AdminAiSettingsController {

    private final AiSettingsService aiSettingsService;

    @GetMapping
    public ResponseEntity<ApiResponse<AiSettingsResponse>> get() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(aiSettingsService.get()));
    }

    @PutMapping("/{feature}")
    public ResponseEntity<ApiResponse<AiFeatureSettingResponse>> update(Principal principal,
                                                                       @PathVariable AiFeature feature,
                                                                       @Valid @RequestBody UpdateAiFeatureSettingRequest request) {
        UUID adminId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(ApiResponse.success("AI settings saved",
                aiSettingsService.update(adminId, feature, request)));
    }
}
