package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.messagedto.CreateLegacyMessageRequest;
import com.ltld.app.legacyvault.dto.messagedto.LegacyMessageResponse;
import com.ltld.app.legacyvault.dto.messagedto.UpdateLegacyMessageRequest;
import com.ltld.app.legacyvault.enums.LegacyMessageDeliveryTrigger;
import com.ltld.app.legacyvault.enums.LegacyMessageRecipientType;
import com.ltld.app.legacyvault.service.messageservice.LegacyMessageService;
import com.ltld.app.legacyvault.service.messageservice.LegacyMessageService.DecryptedAttachment;
import com.ltld.app.legacyvault.utility.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class LegacyMessageController {

    private final LegacyMessageService legacyMessageService;

    // --- OWNER ENDPOINTS ---

    @PostMapping(value = "/api/vaults/{vaultId}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('VAULT_CREATE') or hasAuthority('VAULT_UPDATE')")
    public ResponseEntity<ApiResponse<LegacyMessageResponse>> createMessage(
            Principal principal,
            @PathVariable UUID vaultId,
            @RequestParam("title") String title,
            @RequestParam("content") String content,
            @RequestParam("recipientType") LegacyMessageRecipientType recipientType,
            @RequestParam(value = "recipientBeneficiaryId", required = false) UUID recipientBeneficiaryId,
            @RequestParam("deliveryTrigger") LegacyMessageDeliveryTrigger deliveryTrigger,
            @RequestParam(value = "deliverDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deliverDate,
            @RequestParam(value = "attachment", required = false) MultipartFile attachment) throws Exception {

        UUID ownerId = UUID.fromString(principal.getName());
        CreateLegacyMessageRequest request = CreateLegacyMessageRequest.builder()
                .title(title)
                .content(content)
                .recipientType(recipientType)
                .recipientBeneficiaryId(recipientBeneficiaryId)
                .deliveryTrigger(deliveryTrigger)
                .deliverDate(deliverDate)
                .build();

        LegacyMessageResponse response = legacyMessageService.createMessage(ownerId, vaultId, request, attachment);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Legacy message created successfully", response));
    }

    @GetMapping("/api/vaults/{vaultId}/messages")
    @PreAuthorize("hasAuthority('VAULT_READ')")
    public ResponseEntity<ApiResponse<List<LegacyMessageResponse>>> getMessages(
            Principal principal,
            @PathVariable UUID vaultId) {
        UUID ownerId = UUID.fromString(principal.getName());
        List<LegacyMessageResponse> messages = legacyMessageService.getMessagesByVault(ownerId, vaultId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(messages));
    }

    @GetMapping("/api/vaults/{vaultId}/messages/{messageId}")
    @PreAuthorize("hasAuthority('VAULT_READ')")
    public ResponseEntity<ApiResponse<LegacyMessageResponse>> getMessageById(
            Principal principal,
            @PathVariable UUID vaultId,
            @PathVariable UUID messageId) throws Exception {
        UUID ownerId = UUID.fromString(principal.getName());
        LegacyMessageResponse response = legacyMessageService.getMessageById(ownerId, vaultId, messageId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(response));
    }

    @PutMapping(value = "/api/vaults/{vaultId}/messages/{messageId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('VAULT_CREATE') or hasAuthority('VAULT_UPDATE')")
    public ResponseEntity<ApiResponse<LegacyMessageResponse>> updateMessage(
            Principal principal,
            @PathVariable UUID vaultId,
            @PathVariable UUID messageId,
            @RequestParam("title") String title,
            @RequestParam("content") String content,
            @RequestParam("recipientType") LegacyMessageRecipientType recipientType,
            @RequestParam(value = "recipientBeneficiaryId", required = false) UUID recipientBeneficiaryId,
            @RequestParam("deliveryTrigger") LegacyMessageDeliveryTrigger deliveryTrigger,
            @RequestParam(value = "deliverDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deliverDate,
            @RequestParam(value = "keepExistingAttachment", defaultValue = "true") boolean keepExistingAttachment,
            @RequestParam(value = "attachment", required = false) MultipartFile attachment) throws Exception {

        UUID ownerId = UUID.fromString(principal.getName());
        UpdateLegacyMessageRequest request = UpdateLegacyMessageRequest.builder()
                .title(title)
                .content(content)
                .recipientType(recipientType)
                .recipientBeneficiaryId(recipientBeneficiaryId)
                .deliveryTrigger(deliveryTrigger)
                .deliverDate(deliverDate)
                .keepExistingAttachment(keepExistingAttachment)
                .build();

        LegacyMessageResponse response = legacyMessageService.updateMessage(ownerId, vaultId, messageId, request, attachment);
        return ResponseEntity.ok(ApiResponse.success("Legacy message updated successfully", response));
    }

    @DeleteMapping("/api/vaults/{vaultId}/messages/{messageId}")
    @PreAuthorize("hasAuthority('VAULT_DELETE')")
    public ResponseEntity<ApiResponse<Void>> deleteMessage(
            Principal principal,
            @PathVariable UUID vaultId,
            @PathVariable UUID messageId) {
        UUID ownerId = UUID.fromString(principal.getName());
        legacyMessageService.deleteMessage(ownerId, vaultId, messageId);
        return ResponseEntity.ok(ApiResponse.success("Legacy message deleted successfully"));
    }

    @GetMapping("/api/vaults/{vaultId}/messages/{messageId}/attachment")
    @PreAuthorize("hasAuthority('VAULT_READ')")
    public ResponseEntity<byte[]> downloadAttachment(
            Principal principal,
            @PathVariable UUID vaultId,
            @PathVariable UUID messageId) throws Exception {
        UUID ownerId = UUID.fromString(principal.getName());
        DecryptedAttachment att = legacyMessageService.getAttachment(ownerId, vaultId, messageId);

        MediaType mediaType = att.contentType() != null
                ? MediaType.parseMediaType(att.contentType())
                : MediaType.APPLICATION_OCTET_STREAM;

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(att.filename() != null ? att.filename() : "attachment", StandardCharsets.UTF_8)
                                .build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(att.data());
    }

    // --- BENEFICIARY ENDPOINTS (F4b ready) ---

    @GetMapping("/api/beneficiaries/vaults/{vaultId}/messages")
    @PreAuthorize("hasRole('BENEFICIARY')")
    public ResponseEntity<ApiResponse<List<LegacyMessageResponse>>> getBeneficiaryMessages(
            Principal principal,
            @PathVariable UUID vaultId) {
        UUID userId = UUID.fromString(principal.getName());
        List<LegacyMessageResponse> messages = legacyMessageService.getMessagesForBeneficiary(userId, vaultId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(messages));
    }

    @GetMapping("/api/beneficiaries/vaults/{vaultId}/messages/{messageId}")
    @PreAuthorize("hasRole('BENEFICIARY')")
    public ResponseEntity<ApiResponse<LegacyMessageResponse>> getBeneficiaryMessageDetail(
            Principal principal,
            @PathVariable UUID vaultId,
            @PathVariable UUID messageId) throws Exception {
        UUID userId = UUID.fromString(principal.getName());
        LegacyMessageResponse response = legacyMessageService.getMessageDetailForBeneficiary(userId, vaultId, messageId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(response));
    }

    @GetMapping("/api/beneficiaries/vaults/{vaultId}/messages/{messageId}/attachment")
    @PreAuthorize("hasRole('BENEFICIARY')")
    public ResponseEntity<byte[]> downloadBeneficiaryAttachment(
            Principal principal,
            @PathVariable UUID vaultId,
            @PathVariable UUID messageId) throws Exception {
        UUID userId = UUID.fromString(principal.getName());
        DecryptedAttachment att = legacyMessageService.getAttachmentForBeneficiary(userId, vaultId, messageId);

        MediaType mediaType = att.contentType() != null
                ? MediaType.parseMediaType(att.contentType())
                : MediaType.APPLICATION_OCTET_STREAM;

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(att.filename() != null ? att.filename() : "attachment", StandardCharsets.UTF_8)
                                .build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(att.data());
    }
}

