package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.legalverificationdto.DocumentVerificationResponseDto;
import com.ltld.app.legacyvault.dto.legalverificationdto.RejectVerificationRequestDto;
import com.ltld.app.legacyvault.dto.legalverificationdto.SignVerificationRequestDto;
import com.ltld.app.legacyvault.dto.legalverificationdto.VerificationRequestResponseDto;
import com.ltld.app.legacyvault.enums.LegalVerificationStatus;
import com.ltld.app.legacyvault.service.legalverificationservice.LegalVerificationService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@PreAuthorize("hasRole('LEGAL_VERIFIER') and hasAuthority('LEGAL_REVIEW')")
@RestController
@RequestMapping("/api/verifier/requests")
@RequiredArgsConstructor
public class LegalVerificationController {

    private final LegalVerificationService service;

    // UC12: danh sách hồ sơ đang chờ duyệt (lọc bỏ hồ sơ có xung đột lợi ích)
    @GetMapping
    public ResponseEntity<ApiResponse<List<VerificationRequestResponseDto>>> getPending(
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.success(service.getPendingRequests(verifierId(jwt))));
    }

    // UC15: lịch sử hồ sơ đã xác minh (lọc tùy chọn)
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<VerificationRequestResponseDto>>> getHistory(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) LegalVerificationStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(
                service.getHistory(verifierId(jwt), status, from, to)));
    }

    // UC12: xem chi tiết 1 hồ sơ (trả 404 nếu xung đột lợi ích)
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<VerificationRequestResponseDto>> getById(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.success(service.getById(id, verifierId(jwt))));
    }

    // UC13: xác thực tài liệu (mock) (trả 404 nếu xung đột lợi ích)
    @GetMapping("/{id}/document/verify")
    public ResponseEntity<ApiResponse<DocumentVerificationResponseDto>> verifyDocument(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.success(service.verifyDocument(id, verifierId(jwt))));
    }

    // UC12: phê duyệt (không cần body)
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<VerificationRequestResponseDto>> approve(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.success(
                "Đã phê duyệt hồ sơ", service.approve(id, verifierId(jwt))));
    }

    // UC12: từ chối
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<VerificationRequestResponseDto>> reject(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RejectVerificationRequestDto dto) {
        return ResponseEntity.ok(ApiResponse.success(
                "Đã từ chối", service.reject(id, verifierId(jwt), dto)));
    }

    // UC14: gửi OTP ký xác nhận mở khóa Vault qua email verifier
    @PostMapping("/{id}/sign/otp")
    public ResponseEntity<ApiResponse<Void>> sendSignOtp(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        service.sendSignOtp(id, verifierId(jwt));
        return ResponseEntity.ok(ApiResponse.success("Mã xác nhận đã được gửi đến email của bạn."));
    }

    // UC14: ký xác nhận, mở khóa Vault
    @PostMapping("/{id}/sign")
    public ResponseEntity<ApiResponse<VerificationRequestResponseDto>> sign(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SignVerificationRequestDto dto) {
        return ResponseEntity.ok(ApiResponse.success(
                "Đã ký và mở khóa Vault", service.sign(id, verifierId(jwt), dto)));
    }

    // userId nằm ở claim "sub" của token
    private UUID verifierId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}