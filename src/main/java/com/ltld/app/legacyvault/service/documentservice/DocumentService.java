package com.ltld.app.legacyvault.service.documentservice;

import com.ltld.app.legacyvault.dto.beneficiarydto.DocumentDownloadResponse;
import com.ltld.app.legacyvault.dto.documentdto.DocumentResponse;
import com.ltld.app.legacyvault.entity.LegalDocument;
import com.ltld.app.legacyvault.enums.DocumentType;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface DocumentService {
    // FR-04: Upload tài liệu vào Vault (mã hóa E2E)
    LegalDocument uploadDocument(UUID ownerId, UUID vaultId, DocumentType type, MultipartFile file) throws Exception;

    // FR-04: Liệt kê tài liệu trong Vault (chỉ chủ sở hữu)
    List<DocumentResponse> listDocuments(UUID ownerId, UUID vaultId);

    // FR-04: Xóa tài liệu pháp lý khỏi Vault (chỉ chủ sở hữu, xóa file sau commit)
    void deleteDocument(UUID ownerId, UUID vaultId, UUID documentId);

    // FR-18: Owner tải xuống tài liệu để xem (giải mã trong RAM, không lưu lại)
    DocumentDownloadResponse downloadDocumentForOwner(UUID ownerId, UUID vaultId, UUID documentId) throws Exception;
}
