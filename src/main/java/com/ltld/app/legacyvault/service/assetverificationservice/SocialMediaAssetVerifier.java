package com.ltld.app.legacyvault.service.assetverificationservice;

import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.entity.LegalDocument;
import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.AssetVerificationMethod;
import com.ltld.app.legacyvault.enums.VerificationProvider;
import com.ltld.app.legacyvault.exception.VaultException;
import com.ltld.app.legacyvault.repository.LegalDocumentRepository;
import com.ltld.app.legacyvault.service.vaultservice.assetschema.FlatJson;
import com.ltld.app.legacyvault.utility.OtpGenerator;
import com.ltld.app.legacyvault.utility.OtpHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * C.3.6: Xác thực tài sản SOCIAL_MEDIA (Facebook, Instagram, TikTok, X, LinkedIn, YouTube):
 * - BIO_CHALLENGE_MOCK (SANDBOX): tạo mã thử thách dạng LV-XXXXXX để Owner dán vào tiểu sử (bio) và kiểm tra (mô phỏng).
 * - SCREENSHOT_UPLOAD (REAL): ghi nhận bằng chứng ảnh chụp trang cá nhân qua kho tài liệu mã hóa hoặc mã băm SHA-256 của tệp.
 */
@Component
@RequiredArgsConstructor
public class SocialMediaAssetVerifier implements AssetVerifier {

    private static final int CHALLENGE_TTL_MINUTES = 15;
    private static final int MAX_CACHE_SIZE = 1000;
    private static final Pattern BIO_CODE_PATTERN = Pattern.compile("(LV-\\d{6})", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHA256_HEX_PATTERN = Pattern.compile("^[0-9a-fA-F]{64}$");

    private final OtpGenerator otpGenerator;
    private final OtpHasher otpHasher;
    private final LegalDocumentRepository legalDocumentRepository;

    private final Map<UUID, String> lastChallengeByAsset = new ConcurrentHashMap<>();

    @Override
    public AssetType type() {
        return AssetType.SOCIAL_MEDIA;
    }

    @Override
    public List<AssetVerificationMethod> methods() {
        return List.of(AssetVerificationMethod.BIO_CHALLENGE_MOCK, AssetVerificationMethod.SCREENSHOT_UPLOAD);
    }

    @Override
    public StartResult start(DigitalAsset asset, AssetVerificationMethod method, UUID ownerId) {
        Map<String, String> fields = FlatJson.decode(asset.getPublicFieldsJson());
        String platform = fields.getOrDefault("platform", "Social Media").trim();
        String handle = requireHandle(fields.get("usernameOrHandle"));

        return switch (method) {
            case BIO_CHALLENGE_MOCK -> startBioChallenge(asset, platform, handle);
            case SCREENSHOT_UPLOAD -> new StartResult(
                    VerificationProvider.REAL,
                    "Upload a screenshot of your " + platform + " profile (" + handle + ") and confirm",
                    null,
                    Map.of("platform", platform, "usernameOrHandle", handle),
                    LocalDateTime.now().plusMinutes(CHALLENGE_TTL_MINUTES)
            );
            default -> throw new VaultException("Unsupported verification method for SOCIAL_MEDIA: " + method,
                    HttpStatus.BAD_REQUEST);
        };
    }

    @Override
    public VerifyResult confirm(DigitalAsset asset, AssetVerificationMethod method,
                                Map<String, String> payload, UUID ownerId) {
        String cachedHash = asset.getId() != null ? lastChallengeByAsset.get(asset.getId()) : null;
        return confirm(asset, method, payload, ownerId, cachedHash);
    }

    public VerifyResult confirm(DigitalAsset asset, AssetVerificationMethod method,
                                Map<String, String> payload, UUID ownerId, String challengeHash) {
        Map<String, String> fields = FlatJson.decode(asset.getPublicFieldsJson());
        String platform = fields.getOrDefault("platform", "Social Media").trim();
        String handle = requireHandle(fields.get("usernameOrHandle"));
        Map<String, String> safePayload = payload == null ? Map.of() : payload;

        return switch (method) {
            case BIO_CHALLENGE_MOCK -> confirmBioChallenge(asset, platform, handle, safePayload, challengeHash);
            case SCREENSHOT_UPLOAD -> confirmScreenshotUpload(asset, platform, handle, safePayload);
            default -> throw new VaultException("Unsupported verification method for SOCIAL_MEDIA: " + method,
                    HttpStatus.BAD_REQUEST);
        };
    }

    // =========================================================
    // 1. BIO_CHALLENGE_MOCK
    // =========================================================

    private StartResult startBioChallenge(DigitalAsset asset, String platform, String handle) {
        String challengeCode = "LV-" + otpGenerator.generate();
        UUID saltId = asset.getId() != null ? asset.getId() : new UUID(0L, 0L);
        String challengeHash = otpHasher.hash(saltId, challengeCode);
        if (asset.getId() != null) {
            if (lastChallengeByAsset.size() >= MAX_CACHE_SIZE) {
                lastChallengeByAsset.clear();
            }
            lastChallengeByAsset.put(asset.getId(), challengeHash);
        }

        Map<String, String> clientData = new LinkedHashMap<>();
        clientData.put("platform", platform);
        clientData.put("usernameOrHandle", handle);
        clientData.put("challengeCode", challengeCode);
        clientData.put("mode", "SANDBOX");

        return new StartResult(
                VerificationProvider.SANDBOX,
                "Paste code " + challengeCode + " into your " + platform + " bio (" + handle + "), then confirm (Sandbox)",
                challengeHash,
                clientData,
                LocalDateTime.now().plusMinutes(CHALLENGE_TTL_MINUTES)
        );
    }

    private VerifyResult confirmBioChallenge(DigitalAsset asset, String platform, String handle,
                                             Map<String, String> payload, String challengeHash) {
        String rawInput = firstNonBlank(payload, "challengeCode", "code", "otp", "bio", "bioText");
        if (rawInput == null) {
            return new VerifyResult(false, VerificationProvider.SANDBOX,
                    "Bio challenge code (LV-XXXXXX) is required", Map.of());
        }
        if (challengeHash == null || challengeHash.isBlank()) {
            return new VerifyResult(false, VerificationProvider.SANDBOX,
                    "Bio challenge has not been started", Map.of());
        }

        String token = extractBioChallengeCode(rawInput);
        UUID saltId = asset.getId() != null ? asset.getId() : new UUID(0L, 0L);
        boolean matched = otpHasher.matches(saltId, token, challengeHash)
                || CryptoVerificationUtils.constantTimeEquals(CryptoVerificationUtils.sha256Hex(token), challengeHash);

        if (asset.getId() != null) {
            lastChallengeByAsset.remove(asset.getId());
        }

        if (!matched) {
            return new VerifyResult(false, VerificationProvider.SANDBOX,
                    "Bio challenge code not found or does not match active challenge", Map.of());
        }

        Map<String, String> evidence = new LinkedHashMap<>();
        evidence.put("platform", platform);
        evidence.put("usernameOrHandle", handle);
        evidence.put("challengeCode", token);
        evidence.put("mode", "SANDBOX");
        return new VerifyResult(true, VerificationProvider.SANDBOX,
                "Social media bio challenge verified (Sandbox)", evidence);
    }

    private static String extractBioChallengeCode(String input) {
        Matcher m = BIO_CODE_PATTERN.matcher(input);
        if (m.find()) {
            return m.group(1).toUpperCase(Locale.ROOT);
        }
        String trimmed = input.trim();
        if (trimmed.matches("^\\d{6}$")) {
            return "LV-" + trimmed;
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    // =========================================================
    // 2. SCREENSHOT_UPLOAD
    // =========================================================

    private VerifyResult confirmScreenshotUpload(DigitalAsset asset, String platform, String handle,
                                                 Map<String, String> payload) {
        String docIdStr = firstNonBlank(payload, "documentId", "evidenceDocumentId");
        String rawHash = firstNonBlank(payload, "sha256", "fileHash", "hash");
        String fileName = firstNonBlank(payload, "fileName");

        LegalDocument linkedDoc = null;
        if (docIdStr != null) {
            UUID docId;
            try {
                docId = UUID.fromString(docIdStr);
            } catch (IllegalArgumentException e) {
                return new VerifyResult(false, VerificationProvider.REAL,
                        "Invalid documentId UUID format", Map.of());
            }
            Optional<LegalDocument> found = (asset.getVault() != null && asset.getVault().getId() != null)
                    ? legalDocumentRepository.findByIdAndVaultId(docId, asset.getVault().getId())
                    : legalDocumentRepository.findById(docId);
            if (found.isEmpty()) {
                return new VerifyResult(false, VerificationProvider.REAL,
                        "Screenshot document not found in vault", Map.of());
            }
            linkedDoc = found.get();
        } else if (asset.getLinkedDocumentGroupId() != null) {
            linkedDoc = legalDocumentRepository
                    .findFirstByDocumentGroupIdOrderByVersionDesc(asset.getLinkedDocumentGroupId())
                    .orElse(null);
        }

        String verifiedHash = null;
        if (rawHash != null) {
            if (!SHA256_HEX_PATTERN.matcher(rawHash).matches()) {
                return new VerifyResult(false, VerificationProvider.REAL,
                        "Screenshot SHA-256 hash must be a 64-character hex string", Map.of());
            }
            verifiedHash = rawHash.toLowerCase(Locale.ROOT);
        } else if (linkedDoc != null) {
            verifiedHash = CryptoVerificationUtils.sha256Hex(
                    linkedDoc.getId() + ":" + linkedDoc.getFileName() + ":" + linkedDoc.getFileUrlEncrypted());
        } else if (asset.getAttachmentUrl() != null && !asset.getAttachmentUrl().isBlank()) {
            verifiedHash = CryptoVerificationUtils.sha256Hex(asset.getAttachmentUrl().trim());
        }

        if (verifiedHash == null) {
            return new VerifyResult(false, VerificationProvider.REAL,
                    "Screenshot documentId or 64-character SHA-256 fileHash is required", Map.of());
        }

        Map<String, String> evidence = new LinkedHashMap<>();
        evidence.put("platform", platform);
        evidence.put("usernameOrHandle", handle);
        evidence.put("sha256", verifiedHash);
        if (linkedDoc != null) {
            evidence.put("documentId", linkedDoc.getId().toString());
            if (linkedDoc.getFileName() != null) {
                evidence.put("fileName", linkedDoc.getFileName());
            }
        } else if (fileName != null) {
            evidence.put("fileName", fileName);
        }

        return new VerifyResult(true, VerificationProvider.REAL,
                "Social media profile screenshot evidence recorded (SHA-256 verified)", evidence);
    }

    private static String requireHandle(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new VaultException("Social media username/handle (usernameOrHandle) is missing",
                    HttpStatus.BAD_REQUEST);
        }
        return raw.trim();
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
