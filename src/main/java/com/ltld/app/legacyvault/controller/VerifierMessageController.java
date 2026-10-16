package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.executordto.SendVerificationMessageRequest;
import com.ltld.app.legacyvault.dto.executordto.VerificationMessageResponse;
import com.ltld.app.legacyvault.dto.legalverificationdto.VerificationDocumentDto;
import com.ltld.app.legacyvault.service.legalverificationservice.VerifierMessageService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/** D2: Legal Verifier đọc và trả lời tin nhắn trao đổi với Executor. */
@PreAuthorize("hasRole('LEGAL_VERIFIER') and hasAuthority('LEGAL_REVIEW')")
@RestController
@RequestMapping("/api/verifier/requests")
@RequiredArgsConstructor
public class VerifierMessageController {

    private final VerifierMessageService service;

    @GetMapping("/{id}/messages")
    public ResponseEntity<ApiResponse<List<VerificationMessageResponse>>> history(
            @PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.success(
                "Verification messages retrieved", service.history(id, verifierId(jwt))));
    }

    @PostMapping(value = "/{id}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<VerificationMessageResponse>> send(
            @PathVariable UUID id,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @Valid @ModelAttribute SendVerificationMessageRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Verification message sent", service.send(id, verifierId(jwt), file, request)));
    }

    @GetMapping("/{id}/messages/{messageId}/attachment")
    public ResponseEntity<byte[]> attachment(
            @PathVariable UUID id, @PathVariable UUID messageId,
            @AuthenticationPrincipal Jwt jwt) {
        VerificationDocumentDto doc = service.getAttachment(id, messageId, verifierId(jwt));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(doc.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(doc.getFileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(doc.getContent());
    }

    private UUID verifierId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}