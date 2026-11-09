package com.ltld.app.legacyvault.service.assetverificationservice;

import com.ltld.app.legacyvault.entity.LegalDocument;
import com.ltld.app.legacyvault.repository.LegalDocumentRepository;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import com.ltld.app.legacyvault.service.fileservice.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/** Giải mã tệp trong kho tài liệu của một vault và tính SHA-256 phía server. */
@Component
@RequiredArgsConstructor
public class VaultFileHasher {

    public record HashedFile(UUID documentId, String sha256) {
    }

    private final LegalDocumentRepository legalDocumentRepository;
    private final FileStorageService fileStorageService;
    private final CryptoService cryptoService;

    /** Rỗng nếu tài liệu không thuộc vault; ném IllegalStateException nếu không đọc hoặc băm được. */
    public Optional<HashedFile> hash(UUID vaultId, UUID documentId) {
        Optional<LegalDocument> found = legalDocumentRepository.findByIdAndVaultId(documentId, vaultId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        LegalDocument doc = found.get();
        try {
            byte[] plain = cryptoService.decryptBytes(fileStorageService.readFile(doc.getFileUrlEncrypted()));
            String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(plain));
            return Optional.of(new HashedFile(doc.getId(), sha256));
        } catch (Exception e) {
            throw new IllegalStateException("Could not read document " + documentId, e);
        }
    }
}
