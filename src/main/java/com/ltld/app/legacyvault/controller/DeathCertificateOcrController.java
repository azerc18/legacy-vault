package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.ocrdto.DeathCertificateOcrResponseDto;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.service.ocrservice.DeathCertificateOcrService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class DeathCertificateOcrController {

    private final DeathCertificateOcrService deathCertificateOcrService;

    @PostMapping(
            value = "/api/executor-assignments/{assignmentId}/ocr-death-certificate",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<ApiResponse<DeathCertificateOcrResponseDto>> analyzeForExecutor(
            @PathVariable UUID assignmentId,
            @RequestPart(value = "file", required = false) MultipartFile file,
            Principal principal) {
        DeathCertificateOcrResponseDto result =
                deathCertificateOcrService.analyzeForExecutor(assignmentId, principal.getName(), file);
        return ResponseEntity.ok(ApiResponse.success("AI OCR analysis completed", result));
    }

    @PreAuthorize("hasRole('LEGAL_VERIFIER') and hasAuthority('LEGAL_REVIEW')")
    @PostMapping("/api/verifier/requests/{id}/document/ocr")
    public ResponseEntity<ApiResponse<DeathCertificateOcrResponseDto>> analyzeForVerifier(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        DeathCertificateOcrResponseDto result =
                deathCertificateOcrService.analyzeForVerifier(id, extractVerifierId(jwt));
        return ResponseEntity.ok(ApiResponse.success("AI OCR analysis completed", result));
    }

    private UUID extractVerifierId(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessException("Invalid session.", HttpStatus.UNAUTHORIZED);
        }
    }
}
