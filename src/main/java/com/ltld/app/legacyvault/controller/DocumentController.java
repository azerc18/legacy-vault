package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.beneficiarydto.DocumentDownloadResponse;
import com.ltld.app.legacyvault.dto.documentdto.DocumentResponse;
import com.ltld.app.legacyvault.entity.LegalDocument;
import com.ltld.app.legacyvault.enums.DocumentType;
import com.ltld.app.legacyvault.service.documentservice.DocumentService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    // FR-04: Upload tài liệu vào Vault (multipart/form-data vì gửi file khác với JSON)
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    @PreAuthorize("hasAuthority('VAULT_CREATE')")
    public ResponseEntity<ApiResponse<DocumentResponse>> uploadDocument(
            Principal principal,
            @RequestParam("vaultId") UUID vaultId,
            @RequestParam("type") DocumentType type,
            @RequestParam("file") MultipartFile file) throws Exception {

        UUID ownerId = UUID.fromString(principal.getName());
        LegalDocument document = documentService.uploadDocument(ownerId, vaultId, type, file);

        DocumentResponse response = DocumentResponse.builder()
                .id(document.getId())
                .vaultId(document.getVault().getId())
                .documentType(document.getDocumentType())
                .fileName(document.getFileName())
                .uploadedAt(document.getUploadedAt())
                .build();

        return ResponseEntity.ok(ApiResponse.success("Document uploaded and encrypted securely", response));
    }

    // FR-04: Liệt kê tài liệu trong Vault (chỉ chủ sở hữu)
    @GetMapping("/{vaultId}")
    @PreAuthorize("hasAuthority('VAULT_READ')")
    public ResponseEntity<ApiResponse<List<DocumentResponse>>> listDocuments(
            Principal principal,
            @PathVariable UUID vaultId) {

        UUID ownerId = UUID.fromString(principal.getName());
        List<DocumentResponse> list = documentService.listDocuments(ownerId, vaultId);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách tài liệu thành công", list));
    }

    // FR-04: Xóa tài liệu pháp lý khỏi Vault (chỉ chủ sở hữu)
    @DeleteMapping("/{vaultId}/{documentId}")
    @PreAuthorize("hasAuthority('VAULT_DELETE')")
    public ResponseEntity<ApiResponse<Void>> deleteDocument(
            Principal principal,
            @PathVariable UUID vaultId,
            @PathVariable UUID documentId) {

        UUID ownerId = UUID.fromString(principal.getName());
        documentService.deleteDocument(ownerId, vaultId, documentId);
        return ResponseEntity.ok(ApiResponse.success("Document deleted successfully"));
    }

    // FR-18: Owner tải xuống tài liệu để xem — giải mã trong RAM, gửi về thẳng browser
    @GetMapping("/{vaultId}/{documentId}/download")
    @PreAuthorize("hasAuthority('VAULT_READ')")
    public ResponseEntity<byte[]> downloadDocument(
            Principal principal,
            @PathVariable UUID vaultId,
            @PathVariable UUID documentId) throws Exception {

        UUID ownerId = UUID.fromString(principal.getName());
        DocumentDownloadResponse download = documentService.downloadDocumentForOwner(ownerId, vaultId, documentId);

        // Encode tên file để tránh lỗi non-ASCII trên các browser khác nhau
        String encodedName = URLEncoder.encode(download.getFileName(), StandardCharsets.UTF_8)
                .replace("+", "%20");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(download.getContentType()));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(download.getFileName(), StandardCharsets.UTF_8)
                .build());

        return ResponseEntity.ok()
                .headers(headers)
                .body(download.getContent());
    }
}
