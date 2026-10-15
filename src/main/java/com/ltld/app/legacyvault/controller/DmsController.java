package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.dmsdto.DmsConfigRequest;
import com.ltld.app.legacyvault.dto.dmsdto.DmsConfigResponse;
import com.ltld.app.legacyvault.service.dmsservice.DmsConfigService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

// FR-03: Owner cấu hình Dead Man's Switch và check-in.
@RestController
@RequestMapping("/api/dms")
@PreAuthorize("hasRole('OWNER')")
@RequiredArgsConstructor
public class DmsController {

    private final DmsConfigService dmsConfigService;

    @GetMapping
    public ResponseEntity<ApiResponse<DmsConfigResponse>> getConfig(Principal principal) {
        UUID ownerId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(ApiResponse.success("Lấy cấu hình Dead Man's Switch thành công",
                dmsConfigService.getConfig(ownerId)));
    }

    // Tạo mới trả 201, cập nhật trả 200.
    @PutMapping
    public ResponseEntity<ApiResponse<DmsConfigResponse>> upsertConfig(
            Principal principal,
            @Valid @RequestBody DmsConfigRequest request) {
        UUID ownerId = UUID.fromString(principal.getName());
        DmsConfigService.UpsertResult result = dmsConfigService.upsertConfig(ownerId, request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ApiResponse.success("Lưu cấu hình Dead Man's Switch thành công", result.config()));
    }

    @PostMapping("/check-in")
    public ResponseEntity<ApiResponse<DmsConfigResponse>> checkIn(Principal principal) {
        UUID ownerId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(ApiResponse.success("Check-in thành công", dmsConfigService.checkIn(ownerId)));
    }
}
