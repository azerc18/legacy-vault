package com.ltld.app.legacyvault.service.assetverificationservice;

import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.AssetVerificationMethod;
import com.ltld.app.legacyvault.enums.VerificationProvider;
import com.ltld.app.legacyvault.exception.VaultException;
import com.ltld.app.legacyvault.service.vaultservice.assetschema.FlatJson;
import com.ltld.app.legacyvault.utility.EmailSender;
import com.ltld.app.legacyvault.utility.OtpGenerator;
import com.ltld.app.legacyvault.utility.OtpHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * C.3.4: Xác thực tài sản EXCHANGE_ACCOUNT.
 * - EXCHANGE_EMAIL_CODE (SANDBOX, nhãn "Mô phỏng"): gửi mã 6 chữ số tới email đăng ký sàn do Owner khai
 *   (registeredEmail, hoặc accountId nếu accountId là email). Chỉ chứng minh Owner đọc được hộp thư đó,
 *   KHÔNG chứng minh tài khoản trên sàn tồn tại hay thuộc về Owner.
 * - API_READONLY_MOCK (SANDBOX): Owner dán một chuỗi giống khóa API chỉ-đọc; hệ thống CHỈ kiểm định dạng, không gọi sàn,
 *   không lưu và không log khóa (bằng chứng chỉ có 4 ký tự cuối).
 * - SCREENSHOT_UPLOAD (SANDBOX): Owner tải ảnh chụp trang tài khoản sàn lên két; server băm SHA-256, không đọc nội dung.
 * Bằng chứng chỉ lưu email đã che, không lưu email đầy đủ.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExchangeAssetVerifier implements AssetVerifier {

    private static final int CHALLENGE_TTL_MINUTES = 5;
    private static final Pattern API_KEY_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{16,128}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final OtpGenerator otpGenerator;
    private final OtpHasher otpHasher;
    private final EmailSender emailSender;
    private final VaultFileHasher fileHasher;

    @Override
    public AssetType type() {
        return AssetType.EXCHANGE_ACCOUNT;
    }

    @Override
    public List<AssetVerificationMethod> methods() {
        return List.of(AssetVerificationMethod.EXCHANGE_EMAIL_CODE,
                AssetVerificationMethod.API_READONLY_MOCK,
                AssetVerificationMethod.SCREENSHOT_UPLOAD);
    }

    @Override
    public StartResult start(DigitalAsset asset, AssetVerificationMethod method, UUID ownerId) {
        return switch (method) {
            case EXCHANGE_EMAIL_CODE -> startEmailCode(asset);
            case API_READONLY_MOCK -> sandboxStart(
                    "Paste a read-only API key (simulated, nothing is stored)");
            case SCREENSHOT_UPLOAD -> sandboxStart(
                    "Upload a screenshot of your exchange account page to the vault, then confirm");
            default -> throw unsupported(method);
        };
    }

    private StartResult startEmailCode(DigitalAsset asset) {
        String email = resolveEmail(asset);
        String otp = otpGenerator.generate();
        String challengeHash = otpHasher.hash(saltId(asset), otp);

        emailSender.sendAssetVerificationOtpEmail(email, otp, CHALLENGE_TTL_MINUTES);

        String masked = EmailAssetVerifier.maskEmail(email);
        Map<String, String> clientData = new LinkedHashMap<>();
        clientData.put("maskedEmail", masked);
        return new StartResult(VerificationProvider.SANDBOX,
                "Verification code sent to " + masked,
                challengeHash, clientData,
                LocalDateTime.now().plusMinutes(CHALLENGE_TTL_MINUTES));
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
            case EXCHANGE_EMAIL_CODE -> confirmEmailCode(asset, safePayload, challengeHash);
            case API_READONLY_MOCK -> confirmApiKey(safePayload);
            case SCREENSHOT_UPLOAD -> confirmScreenshot(asset, safePayload);
            default -> throw unsupported(method);
        };
    }

    private VerifyResult confirmEmailCode(DigitalAsset asset, Map<String, String> safePayload, String challengeHash) {
        String code = firstNonBlank(safePayload, "code", "otp", "verificationCode");
        if (code == null) {
            return fail("Verification code is required");
        }
        if (challengeHash == null || challengeHash.isBlank()) {
            return fail("Verification challenge has not been started");
        }
        if (!otpHasher.matches(saltId(asset), code, challengeHash)) {
            return fail("Invalid verification code");
        }

        Map<String, String> evidence = new LinkedHashMap<>();
        evidence.put("check", "EMAIL_CODE");
        evidence.put("maskedEmail", EmailAssetVerifier.maskEmail(resolveEmail(asset)));
        return new VerifyResult(true, VerificationProvider.SANDBOX,
                "Simulated check: you can read the registered email; this does not prove the exchange account is yours",
                evidence);
    }

    private VerifyResult confirmApiKey(Map<String, String> payload) {
        String key = firstNonBlank(payload, "code", "apiKey");
        if (key == null) {
            return fail("A read-only API key is required");
        }
        if (!API_KEY_PATTERN.matcher(key).matches()) {
            return fail("The API key format is not valid (16-128 characters: letters, digits, '-' or '_')");
        }
        // Chỉ giữ 4 ký tự cuối; tuyệt đối không lưu hay log cả khóa
        Map<String, String> evidence = new LinkedHashMap<>();
        evidence.put("check", "API_READONLY");
        evidence.put("mode", "SIMULATED");
        evidence.put("keyLast4", "****" + key.substring(key.length() - 4));
        return new VerifyResult(true, VerificationProvider.SANDBOX,
                "Simulated check: the key format looks valid and a read-only balance was simulated; no exchange was contacted",
                evidence);
    }

    private VerifyResult confirmScreenshot(DigitalAsset asset, Map<String, String> payload) {
        UUID vaultId = asset.getVault() != null ? asset.getVault().getId() : null;
        if (vaultId == null) {
            return fail("Asset is not attached to a vault");
        }
        String docIdStr = firstNonBlank(payload, "documentId");
        if (docIdStr == null) {
            return fail("Upload the screenshot to the vault and send its documentId");
        }
        UUID docId;
        try {
            docId = UUID.fromString(docIdStr);
        } catch (IllegalArgumentException e) {
            return fail("Invalid documentId UUID format");
        }
        try {
            Optional<VaultFileHasher.HashedFile> hashed = fileHasher.hash(vaultId, docId);
            if (hashed.isEmpty()) {
                return fail("Document not found in this vault");
            }
            Map<String, String> evidence = new LinkedHashMap<>();
            evidence.put("documentId", hashed.get().documentId().toString());
            evidence.put("hashAlgorithm", "SHA-256");
            evidence.put("sha256", hashed.get().sha256());
            return new VerifyResult(true, VerificationProvider.SANDBOX,
                    "Screenshot fingerprint (SHA-256) stored; the screenshot content was not read", evidence);
        } catch (IllegalStateException e) {
            log.warn("Could not hash screenshot {} for asset {}: {}", docId, asset.getId(), e.getMessage());
            return fail("Could not read the document, please try again later");
        }
    }

    private static StartResult sandboxStart(String message) {
        return new StartResult(VerificationProvider.SANDBOX, message, null,
                Map.of("mode", "SANDBOX"), LocalDateTime.now().plusMinutes(CHALLENGE_TTL_MINUTES));
    }

    // Ưu tiên registeredEmail; không có thì dùng accountId nếu accountId có dạng email
    private static String resolveEmail(DigitalAsset asset) {
        Map<String, String> fields = FlatJson.decode(asset.getPublicFieldsJson());
        String candidate = firstNonBlank(fields, "registeredEmail");
        if (candidate == null) {
            String accountId = firstNonBlank(fields, "accountId");
            if (accountId != null && EMAIL_PATTERN.matcher(accountId).matches()) {
                candidate = accountId;
            }
        }
        if (candidate == null || !EMAIL_PATTERN.matcher(candidate).matches()) {
            throw new VaultException(
                    "Add the email registered with the exchange to this asset before verifying",
                    HttpStatus.BAD_REQUEST);
        }
        return candidate;
    }

    private static UUID saltId(DigitalAsset asset) {
        return asset.getId() != null ? asset.getId() : new UUID(0L, 0L);
    }

    private static VerifyResult fail(String message) {
        return new VerifyResult(false, VerificationProvider.SANDBOX, message, Map.of());
    }

    private static VaultException unsupported(AssetVerificationMethod method) {
        return new VaultException("Unsupported verification method for EXCHANGE_ACCOUNT: " + method,
                HttpStatus.BAD_REQUEST);
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
