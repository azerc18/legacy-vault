package com.ltld.app.legacyvault.service.assetverificationservice;

import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.entity.DmsConfirmer;
import com.ltld.app.legacyvault.entity.LegalDocument;
import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.AssetVerificationMethod;
import com.ltld.app.legacyvault.enums.DmsConfirmerStatus;
import com.ltld.app.legacyvault.enums.VerificationProvider;
import com.ltld.app.legacyvault.exception.VaultException;
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
import com.ltld.app.legacyvault.repository.DmsConfirmerRepository;
import com.ltld.app.legacyvault.repository.LegalDocumentRepository;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import com.ltld.app.legacyvault.service.fileservice.FileStorageService;
import com.ltld.app.legacyvault.service.vaultservice.assetschema.FlatJson;
import com.ltld.app.legacyvault.utility.EmailSender;
import com.ltld.app.legacyvault.utility.OtpGenerator;
import com.ltld.app.legacyvault.utility.OtpHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.time.Instant;
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
 * - SECOND_PERSON_CONFIRM (REAL): mã gửi tới DMS confirmer của Owner, Owner nhập lại mã; chỉ chứng minh mã đã tới người thứ hai và được nhập lại đúng; không chứng minh họ đã xem bản gốc
 * - DOCUMENT_TIMESTAMP (SANDBOX): Owner tải bản gốc lên két, server băm SHA-256 và gắn mốc thời gian UTC (mô phỏng)
 *   chứng minh tệp đã tồn tại ở thời điểm đó.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentAssetVerifier implements AssetVerifier {

    private static final int CHALLENGE_TTL_MINUTES = 15;
    private static final int SECOND_PERSON_TTL_MINUTES = 60;

    private final LegalDocumentRepository legalDocumentRepository;
    private final FileStorageService fileStorageService;
    private final CryptoService cryptoService;
    private final DmsConfigRepository dmsConfigRepository;
    private final DmsConfirmerRepository dmsConfirmerRepository;
    private final OtpGenerator otpGenerator;
    private final OtpHasher otpHasher;
    private final EmailSender emailSender;

    @Override
    public AssetType type() {
        return AssetType.DOCUMENT;
    }

    @Override
    public List<AssetVerificationMethod> methods() {
        return List.of(
                AssetVerificationMethod.FILE_HASH,
                AssetVerificationMethod.SECOND_PERSON_CONFIRM,
                AssetVerificationMethod.DOCUMENT_TIMESTAMP);
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
            case DOCUMENT_TIMESTAMP -> new StartResult(
                    VerificationProvider.SANDBOX,
                    "Upload the original document to the vault to compute its SHA-256 hash and record a simulated timestamp",
                    null,
                    Map.of("documentTitle", title),
                    LocalDateTime.now().plusMinutes(CHALLENGE_TTL_MINUTES));
            case SECOND_PERSON_CONFIRM -> startSecondPerson(asset, ownerId);
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
            case DOCUMENT_TIMESTAMP -> confirmDocumentTimestamp(asset, safePayload);
            case SECOND_PERSON_CONFIRM -> confirmSecondPerson(asset, safePayload, challengeHash);
            default -> throw unsupported(method);
        };
    }

    private record DocResolveResult(LegalDocument doc, String sha256, String error) {
        boolean ok() {
            return error == null && doc != null && sha256 != null;
        }
    }

    private DocResolveResult resolveAndHashDocument(DigitalAsset asset, Map<String, String> payload) {
        UUID vaultId = asset.getVault() != null ? asset.getVault().getId() : null;
        if (vaultId == null) {
            return new DocResolveResult(null, null, "Asset is not attached to a vault");
        }

        LegalDocument doc;
        String docIdStr = firstNonBlank(payload, "documentId", "evidenceDocumentId");
        if (docIdStr != null) {
            UUID docId;
            try {
                docId = UUID.fromString(docIdStr);
            } catch (IllegalArgumentException e) {
                return new DocResolveResult(null, null, "Invalid documentId UUID format");
            }
            // Chỉ nhận tài liệu nằm trong đúng vault của tài sản
            Optional<LegalDocument> found = legalDocumentRepository.findByIdAndVaultId(docId, vaultId);
            if (found.isEmpty()) {
                return new DocResolveResult(null, null, "Document not found in this vault");
            }
            doc = found.get();
        } else if (asset.getLinkedDocumentGroupId() != null) {
            doc = legalDocumentRepository
                    .findFirstByDocumentGroupIdOrderByVersionDesc(asset.getLinkedDocumentGroupId())
                    .filter(d -> d.getVault() != null && vaultId.equals(d.getVault().getId()))
                    .orElse(null);
            if (doc == null) {
                return new DocResolveResult(null, null, "Linked document not found in this vault");
            }
        } else {
            return new DocResolveResult(null, null, "Upload the original document to the vault and send its documentId");
        }

        String sha256;
        try {
            byte[] plain = cryptoService.decryptBytes(fileStorageService.readFile(doc.getFileUrlEncrypted()));
            sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(plain));
        } catch (Exception e) {
            log.warn("Could not read document {} for asset {}: {}", doc.getId(), asset.getId(), e.getMessage());
            return new DocResolveResult(null, null, "Could not read the document, please try again later");
        }

        return new DocResolveResult(doc, sha256, null);
    }

    private VerifyResult confirmFileHash(DigitalAsset asset, Map<String, String> payload) {
        DocResolveResult res = resolveAndHashDocument(asset, payload);
        if (!res.ok()) {
            return fail(res.error());
        }

        Map<String, String> evidence = new LinkedHashMap<>();
        evidence.put("documentId", res.doc().getId().toString());
        evidence.put("hashAlgorithm", "SHA-256");
        evidence.put("sha256", res.sha256());
        if (res.doc().getFileName() != null) {
            evidence.put("fileName", res.doc().getFileName());
        }
        return new VerifyResult(true, VerificationProvider.REAL,
                "Document fingerprint (SHA-256) stored; this does not prove the document is genuine", evidence);
    }

    private VerifyResult confirmDocumentTimestamp(DigitalAsset asset, Map<String, String> payload) {
        DocResolveResult res = resolveAndHashDocument(asset, payload);
        if (!res.ok()) {
            return failSandbox(res.error());
        }

        Map<String, String> evidence = new LinkedHashMap<>();
        evidence.put("documentId", res.doc().getId().toString());
        evidence.put("sha256", res.sha256());
        evidence.put("timestampedAt", Instant.now().toString());
        evidence.put("mode", "SIMULATED");

        return new VerifyResult(true, VerificationProvider.SANDBOX,
                "Document timestamp recorded with SHA-256 fingerprint (Simulated)", evidence);
    }

    private StartResult startSecondPerson(DigitalAsset asset, UUID ownerId) {
        List<DmsConfirmer> confirmers = dmsConfigRepository.findByOwnerId(ownerId)
                .map(c -> dmsConfirmerRepository.findAllByDmsConfigIdOrderByCreatedAtAsc(c.getId()))
                .orElse(List.of()).stream()
                .filter(c -> c.getStatus() != DmsConfirmerStatus.CONFIRMED_UNREACHABLE)
                .toList();
        if (confirmers.isEmpty()) {
            throw new VaultException(
                    "Add a trusted confirmer in your dead man's switch settings before using this method",
                    HttpStatus.BAD_REQUEST);
        }

        String otp = otpGenerator.generate();
        String challengeHash = otpHasher.hash(saltId(asset), otp);
        for (DmsConfirmer c : confirmers) {
            emailSender.sendSecondPersonConfirmEmail(c.getEmail(), otp, SECOND_PERSON_TTL_MINUTES);
        }

        String masked = confirmers.stream()
                .map(c -> EmailAssetVerifier.maskEmail(c.getEmail()))
                .collect(java.util.stream.Collectors.joining(", "));
        Map<String, String> clientData = new LinkedHashMap<>();
        clientData.put("maskedEmail", masked);
        return new StartResult(VerificationProvider.REAL,
                "A confirmation code was sent to your trusted confirmer(s): " + masked,
                challengeHash, clientData,
                LocalDateTime.now().plusMinutes(SECOND_PERSON_TTL_MINUTES));
    }

    private VerifyResult confirmSecondPerson(DigitalAsset asset, Map<String, String> payload, String challengeHash) {
        String code = firstNonBlank(payload, "code", "otp", "verificationCode");
        if (code == null) {
            return fail("Confirmation code is required");
        }
        if (challengeHash == null || challengeHash.isBlank()) {
            return fail("Verification challenge has not been started");
        }
        if (!otpHasher.matches(saltId(asset), code, challengeHash)) {
            return fail("Invalid confirmation code");
        }
        Map<String, String> evidence = new LinkedHashMap<>();
        evidence.put("check", "SECOND_PERSON_CODE");
        return new VerifyResult(true, VerificationProvider.REAL,
                "The code sent to your trusted confirmer was entered correctly; this shows the code reached them, "
                        + "not that they saw the original or that the document is genuine",
                evidence);
    }

    private static UUID saltId(DigitalAsset asset) {
        return asset.getId() != null ? asset.getId() : new UUID(0L, 0L);
    }

    private static VerifyResult fail(String message) {
        return new VerifyResult(false, VerificationProvider.REAL, message, Map.of());
    }

    private static VerifyResult failSandbox(String message) {
        return new VerifyResult(false, VerificationProvider.SANDBOX, message, Map.of());
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
