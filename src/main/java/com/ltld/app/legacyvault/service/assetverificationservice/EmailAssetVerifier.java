package com.ltld.app.legacyvault.service.assetverificationservice;

import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.AssetVerificationMethod;
import com.ltld.app.legacyvault.enums.VerificationProvider;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.exception.VaultException;
import com.ltld.app.legacyvault.service.authservice.GoogleTokenVerifier;
import com.ltld.app.legacyvault.service.vaultservice.assetschema.FlatJson;
import com.ltld.app.legacyvault.utility.EmailSender;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * C.3.1: Xác thực tài sản EMAIL (Gmail, Outlook, Yahoo, Apple ID):
 * - OTP_EMAIL (REAL): gửi mã OTP 6 chữ số tới chính địa chỉ email đã khai báo (usernameOrHandle).
 * - GOOGLE_LOGIN (REAL): xác thực ID token của Google qua GoogleTokenVerifier và đối chiếu đúng địa chỉ Gmail.
 */
@Component
@RequiredArgsConstructor
public class EmailAssetVerifier implements AssetVerifier {

    private static final int CHALLENGE_TTL_MINUTES = 5;
    private static final int MAX_CACHE_SIZE = 1000;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final OtpGenerator otpGenerator;
    private final OtpHasher otpHasher;
    private final EmailSender emailSender;
    private final GoogleTokenVerifier googleTokenVerifier;

    // Lưu tạm challengeHash gần nhất theo assetId để tương thích cả khi gọi confirm 4 tham số (PR 0)
    private final Map<UUID, String> lastChallengeByAsset = new ConcurrentHashMap<>();

    @Override
    public AssetType type() {
        return AssetType.EMAIL;
    }

    @Override
    public List<AssetVerificationMethod> methods() {
        return List.of(AssetVerificationMethod.OTP_EMAIL, AssetVerificationMethod.GOOGLE_LOGIN);
    }

    @Override
    public StartResult start(DigitalAsset asset, AssetVerificationMethod method, UUID ownerId) {
        Map<String, String> fields = FlatJson.decode(asset.getPublicFieldsJson());
        String email = requireValidEmail(fields.get("usernameOrHandle"));
        String platform = fields.getOrDefault("platform", "").trim();

        return switch (method) {
            case OTP_EMAIL -> startOtpEmail(asset, email);
            case GOOGLE_LOGIN -> startGoogleLogin(email, platform);
            default -> throw new VaultException("Unsupported verification method for EMAIL: " + method,
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
        String email = requireValidEmail(fields.get("usernameOrHandle"));
        String platform = fields.getOrDefault("platform", "").trim();
        Map<String, String> safePayload = payload == null ? Map.of() : payload;

        return switch (method) {
            case OTP_EMAIL -> confirmOtpEmail(asset, email, safePayload, challengeHash);
            case GOOGLE_LOGIN -> confirmGoogleLogin(email, platform, safePayload);
            default -> throw new VaultException("Unsupported verification method for EMAIL: " + method,
                    HttpStatus.BAD_REQUEST);
        };
    }

    private StartResult startOtpEmail(DigitalAsset asset, String email) {
        String otp = otpGenerator.generate();
        UUID saltId = asset.getId() != null ? asset.getId() : new UUID(0L, 0L);
        String challengeHash = otpHasher.hash(saltId, otp);
        if (asset.getId() != null) {
            if (lastChallengeByAsset.size() >= MAX_CACHE_SIZE) {
                lastChallengeByAsset.clear();
            }
            lastChallengeByAsset.put(asset.getId(), challengeHash);
        }

        emailSender.sendEmail(email, otp);

        String masked = maskEmail(email);
        Map<String, String> clientData = new LinkedHashMap<>();
        clientData.put("email", email);
        clientData.put("maskedEmail", masked);

        return new StartResult(
                VerificationProvider.REAL,
                "Verification code sent to " + masked,
                challengeHash,
                clientData,
                LocalDateTime.now().plusMinutes(CHALLENGE_TTL_MINUTES)
        );
    }

    private VerifyResult confirmOtpEmail(DigitalAsset asset, String email,
                                         Map<String, String> payload, String challengeHash) {
        String code = firstNonBlank(payload, "code", "otp", "verificationCode");
        if (code == null) {
            return new VerifyResult(false, VerificationProvider.REAL,
                    "Verification code is required", Map.of());
        }
        if (challengeHash == null || challengeHash.isBlank()) {
            return new VerifyResult(false, VerificationProvider.REAL,
                    "Verification challenge has not been started", Map.of());
        }

        UUID saltId = asset.getId() != null ? asset.getId() : new UUID(0L, 0L);
        boolean matched = otpHasher.matches(saltId, code, challengeHash)
                || CryptoVerificationUtils.constantTimeEquals(CryptoVerificationUtils.sha256Hex(code), challengeHash);

        if (asset.getId() != null) {
            lastChallengeByAsset.remove(asset.getId());
        }

        if (!matched) {
            return new VerifyResult(false, VerificationProvider.REAL,
                    "Invalid verification code", Map.of());
        }

        Map<String, String> evidence = new LinkedHashMap<>();
        evidence.put("verifiedEmail", email.toLowerCase(Locale.ROOT));
        evidence.put("method", AssetVerificationMethod.OTP_EMAIL.name());
        return new VerifyResult(true, VerificationProvider.REAL,
                "Email ownership verified via OTP", evidence);
    }

    private StartResult startGoogleLogin(String email, String platform) {
        requireGmailCompatible(email, platform);
        if (!googleTokenVerifier.isEnabled()) {
            throw new VaultException("Google Sign-In is not configured", HttpStatus.BAD_REQUEST);
        }
        Map<String, String> clientData = new LinkedHashMap<>();
        clientData.put("expectedEmail", email);
        clientData.put("googleClientId", googleTokenVerifier.getClientId());
        return new StartResult(
                VerificationProvider.REAL,
                "Please sign in with Google account " + email,
                null,
                clientData,
                LocalDateTime.now().plusMinutes(10)
        );
    }

    private VerifyResult confirmGoogleLogin(String email, String platform, Map<String, String> payload) {
        requireGmailCompatible(email, platform);
        String idToken = firstNonBlank(payload, "idToken", "credential", "token");
        if (idToken == null) {
            return new VerifyResult(false, VerificationProvider.REAL,
                    "Google ID token is required", Map.of());
        }

        GoogleTokenVerifier.GoogleProfile profile;
        try {
            profile = googleTokenVerifier.verify(idToken);
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.GOOGLE_LOGIN_DISABLED) {
                throw new VaultException(e.getMessage(), HttpStatus.BAD_REQUEST);
            }
            return new VerifyResult(false, VerificationProvider.REAL, e.getMessage(), Map.of());
        }

        if (!emailsEquivalent(profile.email(), email)) {
            return new VerifyResult(false, VerificationProvider.REAL,
                    "Google account email (" + profile.email() + ") does not match declared email (" + email + ")",
                    Map.of());
        }

        Map<String, String> evidence = new LinkedHashMap<>();
        evidence.put("verifiedEmail", profile.email());
        evidence.put("googleSubject", profile.subject() != null ? profile.subject() : "");
        evidence.put("method", AssetVerificationMethod.GOOGLE_LOGIN.name());
        return new VerifyResult(true, VerificationProvider.REAL,
                "Gmail ownership verified via Google Sign-In", evidence);
    }

    private static void requireGmailCompatible(String email, String platform) {
        String lowerEmail = email.toLowerCase(Locale.ROOT);
        boolean isGmailDomain = lowerEmail.endsWith("@gmail.com") || lowerEmail.endsWith("@googlemail.com");
        if (!platform.isEmpty() && !platform.equalsIgnoreCase("Gmail") && !isGmailDomain) {
            throw new VaultException("Google Sign-In verification is only supported for Gmail accounts",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private static boolean emailsEquivalent(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        String normA = normalizeGmailDomain(a.trim().toLowerCase(Locale.ROOT));
        String normB = normalizeGmailDomain(b.trim().toLowerCase(Locale.ROOT));
        return normA.equals(normB);
    }

    private static String normalizeGmailDomain(String email) {
        if (email.endsWith("@googlemail.com")) {
            return email.substring(0, email.length() - "@googlemail.com".length()) + "@gmail.com";
        }
        return email;
    }

    private static String requireValidEmail(String raw) {
        if (raw == null || raw.isBlank() || !EMAIL_PATTERN.matcher(raw.trim()).matches()) {
            throw new VaultException("Invalid or missing email address (usernameOrHandle)", HttpStatus.BAD_REQUEST);
        }
        return raw.trim();
    }

    static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + email.substring(Math.max(0, at));
        }
        return email.charAt(0) + "***" + email.charAt(at - 1) + email.substring(at);
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
