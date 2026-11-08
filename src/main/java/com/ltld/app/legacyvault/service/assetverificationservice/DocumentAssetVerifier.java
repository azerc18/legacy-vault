package com.ltld.app.legacyvault.service.assetverificationservice;

import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.entity.LegalDocument;
import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.AssetVerificationMethod;
import com.ltld.app.legacyvault.enums.VerificationProvider;
import com.ltld.app.legacyvault.exception.VaultException;
import com.ltld.app.legacyvault.repository.LegalDocumentRepository;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import com.ltld.app.legacyvault.service.fileservice.FileStorageService;
import com.ltld.app.legacyvault.service.vaultservice.assetschema.FlatJson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * C.3.7: Xác thực tài sản DOCUMENT.
 * - FILE_HASH (REAL): Owner tải bản gốc lên kho tài liệu của vault, server tự giải mã và băm SHA-256
 *   (không nhận hash do client gửi). Kết quả chỉ chứng minh "tệp này đã được lưu nguyên vẹn từ thời điểm đó",
 *   KHÔNG chứng minh tài liệu là thật; FE ghi rõ điều này ở bước tải tệp.
 * - SECOND_PERSON_CONFIRM: làm ở đợt sau (cần người xác nhận thứ hai).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentAssetVerifier implements AssetVerifier {

    private static final int CHALLENGE_TTL_MINUTES = 15;

    private final LegalDocumentRepository legalDocumentRepository;
    private final FileStorageService fileStorageService;
    private final CryptoService cryptoService;

    @Override
    public AssetType type() {
        return AssetType.DOCUMENT;
    }

    @Override
    public List<AssetVerificationMethod> methods() {
        return List.of(AssetVerificationMethod.FILE_HASH);
    }

    @Override
    public StartResult start(DigitalAsset asset, AssetVerificationMethod method, UUID ownerId) {
        Map<String, String> fields = FlatJson.decode(asset.getPublicFieldsJson());
        String title = fields.getOrDefault("documentTitle", "").trim();

        return switch (method) {
            case FILE_HASH -> new StartResult(
                    VerificationProvider.REAL,
                    "Upload the original document to the vault, then confirm to store its SHA-256 fingerprint",
                    null,
                    Map.of("documentTitle", title),
                    LocalDateTime.now().plusMinutes(CHALLENGE_TTL_MINUTES));
            default -> throw unsupported(method);
        };
    }

    @Override
    public VerifyResult confirm(DigitalAsset asset, AssetVerificationMethod method,
                                Map<String, String> payload, UUID ownerId) {
        return confirm(asset, method, payload, ownerId, null);
    }

    @Override
    public VerifyResult confirm(DigitalAsset asset, AssetVerificationMethod method,
                                Map<String, String> payload, UUID ownerId, String challengeHash) {
        Map<String, String> safePayload = payload == null ? Map.of() : payload;
        return switch (method) {
            case FILE_HASH -> confirmFileHash(asset, safePayload);
            default -> throw unsupported(method);
        };
    }

    private VerifyResult confirmFileHash(DigitalAsset asset, Map<String, String> payload) {
        UUID vaultId = asset.getVault() != null ? asset.getVault().getId() : null;
        if (vaultId == null) {
            return fail("Asset is not attached to a vault");
        }

        LegalDocument doc;
        String docIdStr = firstNonBlank(payload, "documentId", "evidenceDocumentId");
        if (docIdStr != null) {
            UUID docId;
            try {
                docId = UUID.fromString(docIdStr);
            } catch (IllegalArgumentException e) {
                return fail("Invalid documentId UUID format");
            }
            // Chỉ nhận tài liệu nằm trong đúng vault của tài sản
            Optional<LegalDocument> found = legalDocumentRepository.findByIdAndVaultId(docId, vaultId);
            if (found.isEmpty()) {
                return fail("Document not found in this vault");
            }
            doc = found.get();
        } else if (asset.getLinkedDocumentGroupId() != null) {
            doc = legalDocumentRepository
                    .findFirstByDocumentGroupIdOrderByVersionDesc(asset.getLinkedDocumentGroupId())
                    .filter(d -> d.getVault() != null && vaultId.equals(d.getVault().getId()))
                    .orElse(null);
            if (doc == null) {
                return fail("Linked document not found in this vault");
            }
        } else {
            return fail("Upload the original document to the vault and send its documentId");
        }

        String sha256;
        try {
            byte[] plain = cryptoService.decryptBytes(fileStorageService.readFile(doc.getFileUrlEncrypted()));
            sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(plain));
        } catch (Exception e) {
            log.warn("Could not read document {} for asset {}: {}", doc.getId(), asset.getId(), e.getMessage());
            return fail("Could not read the document, please try again later");
        }

        Map<String, String> evidence = new LinkedHashMap<>();
        evidence.put("documentId", doc.getId().toString());
        evidence.put("hashAlgorithm", "SHA-256");
        evidence.put("sha256", sha256);
        if (doc.getFileName() != null) {
            evidence.put("fileName", doc.getFileName());
        }
        return new VerifyResult(true, VerificationProvider.REAL,
                "Document fingerprint (SHA-256) stored; this does not prove the document is genuine", evidence);
    }

    private static VerifyResult fail(String message) {
        return new VerifyResult(false, VerificationProvider.REAL, message, Map.of());
    }

    private static VaultException unsupported(AssetVerificationMethod method) {
        return new VaultException("Unsupported verification method for DOCUMENT: " + method, HttpStatus.BAD_REQUEST);
    }

    private static String firstNonBlank(Map<String, String> map, String... keys) {
        for (String key : keys) {
            String v = map.get(key);
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }
}
