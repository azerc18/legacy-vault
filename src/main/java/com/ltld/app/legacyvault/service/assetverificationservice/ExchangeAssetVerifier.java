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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * C.3.4: Xác thực tài sản EXCHANGE_ACCOUNT.
 * - EXCHANGE_EMAIL_CODE (SANDBOX, nhãn "Mô phỏng"): gửi mã 6 chữ số tới email đăng ký sàn do Owner khai
 *   (registeredEmail, hoặc accountId nếu accountId là email). Chỉ chứng minh Owner đọc được hộp thư đó,
 *   KHÔNG chứng minh tài khoản trên sàn tồn tại hay thuộc về Owner.
 * Bằng chứng chỉ lưu email đã che, không lưu email đầy đủ.
 */
@Component
@RequiredArgsConstructor
public class ExchangeAssetVerifier implements AssetVerifier {

    private static final int CHALLENGE_TTL_MINUTES = 5;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final OtpGenerator otpGenerator;
    private final OtpHasher otpHasher;
    private final EmailSender emailSender;

    @Override
    public AssetType type() {
        return AssetType.EXCHANGE_ACCOUNT;
    }

    @Override
    public List<AssetVerificationMethod> methods() {
        return List.of(AssetVerificationMethod.EXCHANGE_EMAIL_CODE);
    }

    @Override
    public StartResult start(DigitalAsset asset, AssetVerificationMethod method, UUID ownerId) {
        if (method != AssetVerificationMethod.EXCHANGE_EMAIL_CODE) {
            throw unsupported(method);
        }
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
        if (method != AssetVerificationMethod.EXCHANGE_EMAIL_CODE) {
            throw unsupported(method);
        }
        Map<String, String> safePayload = payload == null ? Map.of() : payload;
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
