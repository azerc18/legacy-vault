package com.ltld.app.legacyvault.service.documentservice;

import com.ltld.app.legacyvault.dto.beneficiarydto.DocumentDownloadResponse;
import com.ltld.app.legacyvault.dto.documentdto.DocumentResponse;
import com.ltld.app.legacyvault.entity.LegalDocument;
import com.ltld.app.legacyvault.enums.DocumentType;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface DocumentService {
    // Tải lên tài liệu mới (tự sinh documentGroupId mới, version = 1)
    LegalDocument uploadDocument(UUID ownerId, UUID vaultId, DocumentType type, MultipartFile file) throws Exception;

    // X5: Tải lên phiên bản mới cho nhóm tài liệu đã có (hoặc tài liệu mới nếu documentGroupId == null)
    LegalDocument uploadDocument(UUID ownerId, UUID vaultId, DocumentType type, MultipartFile file, UUID documentGroupId) throws Exception;

    // X5 (m5): Dựng DocumentResponse từ entity với totalVersions thực tế trong DB
    DocumentResponse toResponse(LegalDocument document);

    // Lấy danh sách tài liệu hiện hành của Vault (is_current = true)
    List<DocumentResponse> getDocumentsByVault(UUID vaultId, UUID ownerId);

    // X5: Lấy toàn bộ lịch sử phiên bản của một nhóm tài liệu
    List<DocumentResponse> getDocumentVersions(UUID vaultId, UUID documentGroupId, UUID ownerId);

    DocumentDownloadResponse downloadDocumentForOwner(UUID documentId, UUID ownerId) throws Exception;

    void deleteDocument(UUID documentId, UUID ownerId) throws Exception;
}
