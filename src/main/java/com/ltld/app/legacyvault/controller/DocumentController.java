package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.documentdto.DocumentResponse;
import com.ltld.app.legacyvault.entity.LegalDocument;
import com.ltld.app.legacyvault.enums.DocumentType;
import com.ltld.app.legacyvault.service.documentservice.DocumentService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    // Phải khai báo consumes = "multipart/form-data" vì gửi file khác với gửi JSON bình thường
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
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
                
        return ResponseEntity.ok(ApiResponse.success("Document uploaded and encrypted securely", response));
    }
}
