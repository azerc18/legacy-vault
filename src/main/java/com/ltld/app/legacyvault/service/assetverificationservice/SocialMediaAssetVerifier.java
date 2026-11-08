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
import com.ltld.app.legacyvault.utility.OtpGenerator;
import com.ltld.app.legacyvault.utility.OtpHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * C.3.6: Xác thực tài sản SOCIAL_MEDIA (Facebook, Instagram, TikTok, X, LinkedIn, YouTube):
 * - BIO_CHALLENGE_MOCK (SANDBOX): tạo mã thử thách dạng LV-XXXXXX để Owner dán vào tiểu sử (bio) và kiểm tra (mô phỏng).
 * - SCREENSHOT_UPLOAD (SANDBOX): ghi nhận ảnh chụp trang cá nhân đã tải lên kho tài liệu của vault.
 *   Ảnh do Owner tự nộp nên chỉ là bằng chứng tự khai, KHÔNG tính là xác thực thật. Server tự băm SHA-256 nội dung tệp
 *   (không nhận hash do client gửi) để sau này đối chiếu tệp có bị thay không.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SocialMediaAssetVerifier implements AssetVerifier {

    private static final int CHALLENGE_TTL_MINUTES = 15;
    private static final Pattern BIO_CODE_PATTERN = Pattern.compile("(LV-\\d{6})", Pattern.CASE_INSENSITIVE);

    private final OtpGenerator otpGenerator;
    private final OtpHasher otpHasher;
    private final LegalDocumentRepository legalDocumentRepository;
    private final FileStorageService fileStorageService;
    private final CryptoService cryptoService;

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
                    VerificationProvider.SANDBOX,
                    "Upload a screenshot of your " + platform + " profile (" + handle
                            + ") to the vault documents, then confirm with its documentId",
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
        // Không có challengeHash (lưu ở DB, service truyền vào qua hàm 5 tham số) thì các cách cần mã sẽ trả thất bại
        return confirm(asset, method, payload, ownerId, null);
    }

    // Ghi đè hàm default 5 tham số của AssetVerifier (thêm ở #199). Không đặt @Override để vẫn biên dịch được trước khi #199 merge.
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
        boolean matched = otpHasher.matches(saltId, token, challengeHash);

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
        UUID vaultId = asset.getVault() != null ? asset.getVault().getId() : null;
        if (vaultId == null) {
            return new VerifyResult(false, VerificationProvider.SANDBOX,
                    "Asset is not attached to a vault", Map.of());
        }

        LegalDocument doc;
        String docIdStr = firstNonBlank(payload, "documentId", "evidenceDocumentId");
        if (docIdStr != null) {
            UUID docId;
            try {
                docId = UUID.fromString(docIdStr);
            } catch (IllegalArgumentException e) {
                return new VerifyResult(false, VerificationProvider.SANDBOX,
                        "Invalid documentId UUID format", Map.of());
            }
            // Chỉ nhận tài liệu nằm trong đúng vault của tài sản
            Optional<LegalDocument> found = legalDocumentRepository.findByIdAndVaultId(docId, vaultId);
            if (found.isEmpty()) {
                return new VerifyResult(false, VerificationProvider.SANDBOX,
                        "Screenshot document not found in this vault", Map.of());
            }
            doc = found.get();
        } else if (asset.getLinkedDocumentGroupId() != null) {
            doc = legalDocumentRepository
                    .findFirstByDocumentGroupIdOrderByVersionDesc(asset.getLinkedDocumentGroupId())
                    .filter(d -> d.getVault() != null && vaultId.equals(d.getVault().getId()))
                    .orElse(null);
            if (doc == null) {
                return new VerifyResult(false, VerificationProvider.SANDBOX,
                        "Linked screenshot document not found in this vault", Map.of());
            }
        } else {
            return new VerifyResult(false, VerificationProvider.SANDBOX,
                    "Upload the profile screenshot to the vault documents and send its documentId", Map.of());
        }

        String fileSha256;
        try {
            byte[] plain = cryptoService.decryptBytes(fileStorageService.readFile(doc.getFileUrlEncrypted()));
            fileSha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(plain));
        } catch (Exception e) {
            log.warn("Could not read screenshot document {} for asset {}: {}", doc.getId(), asset.getId(), e.getMessage());
            return new VerifyResult(false, VerificationProvider.SANDBOX,
                    "Could not read the screenshot document, please try again later", Map.of());
        }

        Map<String, String> evidence = new LinkedHashMap<>();
        evidence.put("platform", platform);
        evidence.put("usernameOrHandle", handle);
        evidence.put("documentId", doc.getId().toString());
        evidence.put("sha256", fileSha256);
        if (doc.getFileName() != null) {
            evidence.put("fileName", doc.getFileName());
        }
        evidence.put("mode", "SANDBOX");

        return new VerifyResult(true, VerificationProvider.SANDBOX,
                "Profile screenshot recorded as self-submitted evidence (Sandbox)", evidence);
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
