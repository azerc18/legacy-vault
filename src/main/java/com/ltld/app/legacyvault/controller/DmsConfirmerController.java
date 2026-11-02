package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.dmsdto.DmsConfirmDecisionRequest;
import com.ltld.app.legacyvault.dto.dmsdto.DmsConfirmationDecisionResponse;
import com.ltld.app.legacyvault.dto.dmsdto.DmsConfirmerRequest;
import com.ltld.app.legacyvault.dto.dmsdto.DmsConfirmerResponse;
import com.ltld.app.legacyvault.service.dmsservice.DmsConfirmerService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * X2: Quản lý người xác nhận chéo DMS của Owner, và endpoint công khai để Confirmer gửi quyết định.
 */
@RestController
@RequestMapping("/api/dms")
@RequiredArgsConstructor
public class DmsConfirmerController {

    private final DmsConfirmerService dmsConfirmerService;

    @GetMapping("/confirmers")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<List<DmsConfirmerResponse>>> listConfirmers(Principal principal) {
        UUID ownerId = UUID.fromString(principal.getName());
        return ResponseEntity.ok(ApiResponse.success(
                "Trusted confirmers retrieved successfully",
                dmsConfirmerService.listConfirmers(ownerId)
        ));
    }

    @PostMapping("/confirmers")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<DmsConfirmerResponse>> addConfirmer(
            Principal principal,
            @Valid @RequestBody DmsConfirmerRequest request
    ) {
        UUID ownerId = UUID.fromString(principal.getName());
        DmsConfirmerResponse response = dmsConfirmerService.addConfirmer(ownerId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Trusted confirmer added successfully", response));
    }

    @DeleteMapping("/confirmers/{confirmerId}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<Void>> removeConfirmer(
            Principal principal,
            @PathVariable UUID confirmerId
    ) {
        UUID ownerId = UUID.fromString(principal.getName());
        dmsConfirmerService.removeConfirmer(ownerId, confirmerId);
        return ResponseEntity.ok(ApiResponse.success("Trusted confirmer removed successfully", null));
    }

    /**
     * X2 (C1): link trong email chỉ mở trang FE /dms/confirm (trình quét link của hộp thư có thể tự mở mọi URL);
     * quyết định, đặc biệt UNREACHABLE (không thể hoàn tác), phải là POST do người bấm nút trên trang.
     * Endpoint công khai: cần POST permitAll /api/dms/confirm trong SecurityConfig. Lỗi đi qua GlobalExceptionHandler.
     */
    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<DmsConfirmationDecisionResponse>> confirm(
            @Valid @RequestBody DmsConfirmDecisionRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Confirmation recorded",
                dmsConfirmerService.respondByToken(request.getToken(), request.getDecision(), LocalDateTime.now())));
    }
}
