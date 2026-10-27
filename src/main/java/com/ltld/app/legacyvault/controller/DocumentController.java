package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.beneficiarydto.DocumentDownloadResponse;
import com.ltld.app.legacyvault.dto.documentdto.DocumentResponse;
import com.ltld.app.legacyvault.entity.LegalDocument;
import com.ltld.app.legacyvault.enums.DocumentType;
import com.ltld.app.legacyvault.service.documentservice.DocumentService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    // Phải khai báo consumes = "multipart/form-data" vì gửi file khác với gửi JSON bình thường
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    @PreAuthorize("hasAuthority('VAULT_CREATE')")
    public ResponseEntity<ApiResponse<DocumentResponse>> uploadDocument(
            Principal principal,
            @RequestParam("vaultId") UUID vaultId,
            @RequestParam("type") DocumentType type,
            @RequestParam("file") MultipartFile file) throws Exception {

        // Lấy UUID của người dùng đang gọi API từ JWT Token
        UUID ownerId = UUID.fromString(principal.getName());

        // Ném dữ liệu cho Service xử lý
        LegalDocument document = documentService.uploadDocument(ownerId, vaultId, type, file);
        
        DocumentResponse response = DocumentResponse.builder()
                .id(document.getId())
                .vaultId(document.getVault().getId())
                .documentType(document.getDocumentType())
                .fileName(document.getFileName())
                .uploadedAt(document.getUploadedAt())
                .build();
                
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(ApiResponse.success("Document uploaded and encrypted securely", response));
    }

    // FR-04: Owner xem danh sách tài liệu pháp lý của một Vault
    @GetMapping("/vault/{vaultId}")
    @PreAuthorize("hasAuthority('VAULT_READ')")
    public ResponseEntity<ApiResponse<List<DocumentResponse>>> getDocumentsByVault(
            Principal principal,
            @PathVariable UUID vaultId) {
        UUID ownerId = UUID.fromString(principal.getName());
        List<DocumentResponse> documents = documentService.getDocumentsByVault(vaultId, ownerId);
        return ResponseEntity.ok(ApiResponse.success("Documents retrieved successfully", documents));
    }

    // FR-04: Owner tải xuống và giải mã tài liệu (trả 404 nếu không phải chủ sở hữu)
    @GetMapping("/{documentId}/download")
    @PreAuthorize("hasAuthority('VAULT_READ')")
    public ResponseEntity<byte[]> downloadDocument(
            Principal principal,
            @PathVariable UUID documentId) throws Exception {
        UUID ownerId = UUID.fromString(principal.getName());
        DocumentDownloadResponse file = documentService.downloadDocumentForOwner(documentId, ownerId);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(file.getFileName(), StandardCharsets.UTF_8)
                                .build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(file.getContent());
    }

    // FR-04: Owner xóa tài liệu pháp lý (file vật lý xóa sau khi DB commit)
    @DeleteMapping("/{documentId}")
    @PreAuthorize("hasAuthority('VAULT_DELETE')")
    public ResponseEntity<ApiResponse<Void>> deleteDocument(
            Principal principal,
            @PathVariable UUID documentId) throws Exception {
        UUID ownerId = UUID.fromString(principal.getName());
        documentService.deleteDocument(documentId, ownerId);
        return ResponseEntity.ok(ApiResponse.success("Document deleted successfully"));
    }
}
