package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.RejectVerificationRequestDto;
import com.ltld.app.legacyvault.dto.SignVerificationRequestDto;
import com.ltld.app.legacyvault.dto.VerificationRequestResponseDto;
import com.ltld.app.legacyvault.service.LegalVerificationService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/verifier/requests")
@RequiredArgsConstructor
public class LegalVerificationController {

    private final LegalVerificationService service;

    // FR-12: danh sách hồ sơ đang chờ duyệt
    @GetMapping
    public ResponseEntity<ApiResponse<List<VerificationRequestResponseDto>>> getPending() {
        return ResponseEntity.ok(ApiResponse.success(service.getPendingRequests()));
    }

    // FR-12: xem chi tiết 1 hồ sơ
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<VerificationRequestResponseDto>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(service.getById(id)));
    }

    // FR-12 + FR-14: duyệt và ký
    // Tạm lấy verifierId từ header, sau này đổi sang lấy từ JWT
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<VerificationRequestResponseDto>> approve(
            @PathVariable UUID id,
            @RequestHeader("X-Verifier-Id") UUID verifierId,
            @Valid @RequestBody SignVerificationRequestDto dto) {
        return ResponseEntity.ok(ApiResponse.success(
                "Đã duyệt và ký", service.approveAndSign(id, verifierId, dto)));
    }

    // FR-12: từ chối
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<VerificationRequestResponseDto>> reject(
            @PathVariable UUID id,
            @RequestHeader("X-Verifier-Id") UUID verifierId,
            @Valid @RequestBody RejectVerificationRequestDto dto) {
        return ResponseEntity.ok(ApiResponse.success(
                "Đã từ chối", service.reject(id, verifierId, dto)));
    }
}