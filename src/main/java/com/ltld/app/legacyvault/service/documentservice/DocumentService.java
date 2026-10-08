package com.ltld.app.legacyvault.service.documentservice;

import com.ltld.app.legacyvault.entity.LegalDocument;
import com.ltld.app.legacyvault.enums.DocumentType;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface DocumentService {
    LegalDocument uploadDocument(UUID ownerId, UUID vaultId, DocumentType type, MultipartFile file) throws Exception;
}
