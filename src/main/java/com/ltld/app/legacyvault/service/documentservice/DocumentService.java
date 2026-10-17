package com.ltld.app.legacyvault.service.documentservice;

import com.ltld.app.legacyvault.dto.beneficiarydto.DocumentDownloadResponse;
import com.ltld.app.legacyvault.dto.documentdto.DocumentResponse;
import com.ltld.app.legacyvault.entity.LegalDocument;
import com.ltld.app.legacyvault.enums.DocumentType;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface DocumentService {
    LegalDocument uploadDocument(UUID ownerId, UUID vaultId, DocumentType type, MultipartFile file) throws Exception;
    List<DocumentResponse> getDocumentsByVault(UUID vaultId, UUID ownerId);
    DocumentDownloadResponse downloadDocumentForOwner(UUID documentId, UUID ownerId) throws Exception;
    void deleteDocument(UUID documentId, UUID ownerId) throws Exception;

}
