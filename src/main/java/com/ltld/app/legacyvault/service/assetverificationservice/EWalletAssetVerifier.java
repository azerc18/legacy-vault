package com.ltld.app.legacyvault.service.assetverificationservice;

import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.AssetVerificationMethod;
import com.ltld.app.legacyvault.enums.VerificationProvider;
import com.ltld.app.legacyvault.exception.VaultException;
import com.ltld.app.legacyvault.service.vaultservice.assetschema.FlatJson;
import com.ltld.app.legacyvault.utility.OtpGenerator;
import com.ltld.app.legacyvault.utility.OtpHasher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * C.3.2: Xác thực tài sản E_WALLET (MoMo, ZaloPay, ViettelPay, PayPal):
 * - SMS_CODE_MOCK (SANDBOX): kiểm định dạng SĐT Việt Nam (^(0|\+84)(3|5|7|8|9)\d{8}$), nhận diện nhà mạng theo đầu số và gửi mã SMS mô phỏng.
 * - PAYPAL_LOGIN (SANDBOX): xác thực tài khoản PayPal qua OAuth Sandbox userinfo hoặc mã xác nhận PayPal Sandbox.
 */
@Slf4j
@Component
public class EWalletAssetVerifier implements AssetVerifier {

    private static final int CHALLENGE_TTL_MINUTES = 5;
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(2500))
            .build();
    private static final Pattern VN_PHONE_PATTERN = Pattern.compile("^(0|\\+84)(3|5|7|8|9)\\d{8}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PAYPAL_EMAIL_JSON = Pattern.compile("\"value\"\\s*:\\s*\"([^\"@]+@[^\"]+)\"|"
            + "\"email\"\\s*:\\s*\"([^\"@]+@[^\"]+)\"");

    private final OtpGenerator otpGenerator;
    private final OtpHasher otpHasher;
    private final String paypalClientId;
    private PayPalEmailLookup payPalEmailLookup;


    @FunctionalInterface
    public interface PayPalEmailLookup {
        String fetchVerifiedEmail(String accessToken) throws IOException, InterruptedException;
    }

    @Autowired
    public EWalletAssetVerifier(OtpGenerator otpGenerator,
                                OtpHasher otpHasher,
                                @Value("${app.paypal.client-id:}") String paypalClientId) {
        this.otpGenerator = otpGenerator;
        this.otpHasher = otpHasher;
        this.paypalClientId = paypalClientId == null ? "" : paypalClientId.trim();
        this.payPalEmailLookup = this::fetchPayPalEmailFromSandbox;
    }

    void setPayPalEmailLookup(PayPalEmailLookup payPalEmailLookup) {
        this.payPalEmailLookup = payPalEmailLookup;
    }

    @Override
    public AssetType type() {
        return AssetType.E_WALLET;
    }

    @Override
    public List<AssetVerificationMethod> methods() {
        return List.of(AssetVerificationMethod.SMS_CODE_MOCK, AssetVerificationMethod.PAYPAL_LOGIN);
    }

    @Override
    public StartResult start(DigitalAsset asset, AssetVerificationMethod method, UUID ownerId) {
        Map<String, String> fields = FlatJson.decode(asset.getPublicFieldsJson());
        String platform = fields.getOrDefault("platform", "Other").trim();
        String handle = fields.getOrDefault("usernameOrHandle", "").trim();

        return switch (method) {
            case SMS_CODE_MOCK -> startSmsMock(asset, platform, handle);
            case PAYPAL_LOGIN -> startPayPalLogin(asset, platform, handle);
            default -> throw new VaultException("Unsupported verification method for E_WALLET: " + method,
                    HttpStatus.BAD_REQUEST);
        };
    }

    @Override
    public VerifyResult confirm(DigitalAsset asset, AssetVerificationMethod method,
                                Map<String, String> payload, UUID ownerId) {
        // Mã thử thách lưu ở DB; service truyền vào qua hàm 5 tham số. Gọi hàm này thì các cách cần mã sẽ trả thất bại.
        return confirm(asset, method, payload, ownerId, null);
    }

    // Ghi đè hàm default 5 tham số của AssetVerifier (thêm ở #199). Không đặt @Override để vẫn biên dịch được trước khi #199 merge.
    public VerifyResult confirm(DigitalAsset asset, AssetVerificationMethod method,
                                Map<String, String> payload, UUID ownerId, String challengeHash) {
        Map<String, String> fields = FlatJson.decode(asset.getPublicFieldsJson());
        String platform = fields.getOrDefault("platform", "Other").trim();
        String handle = fields.getOrDefault("usernameOrHandle", "").trim();
        Map<String, String> safePayload = payload == null ? Map.of() : payload;

        return switch (method) {
            case SMS_CODE_MOCK -> confirmSmsMock(asset, platform, handle, safePayload, challengeHash);
            case PAYPAL_LOGIN -> confirmPayPalLogin(asset, platform, handle, safePayload, challengeHash);
            default -> throw new VaultException("Unsupported verification method for E_WALLET: " + method,
                    HttpStatus.BAD_REQUEST);
        };
    }

    // =========================================================
    // 1. SMS_CODE_MOCK
    // =========================================================

    private StartResult startSmsMock(DigitalAsset asset, String platform, String rawPhone) {
        String phone = requireValidVnPhone(rawPhone);
        String carrier = detectVietnameseCarrier(phone);
        String maskedPhone = maskPhone(phone);

        String otp = otpGenerator.generate();
        UUID saltId = asset.getId() != null ? asset.getId() : new UUID(0L, 0L);
        String challengeHash = otpHasher.hash(saltId, otp);

        // Mã mô phỏng chỉ trả về trong clientData.sandboxCode, không ghi log
        Map<String, String> clientData = new LinkedHashMap<>();
        clientData.put("platform", platform);
        clientData.put("maskedPhone", maskedPhone);
        clientData.put("carrier", carrier);
        clientData.put("sandboxCode", otp);
        clientData.put("mode", "SANDBOX");

        return new StartResult(
                VerificationProvider.SANDBOX,
                "Simulated SMS verification code sent to " + maskedPhone + " (" + carrier + " - Sandbox)",
                challengeHash,
                clientData,
                LocalDateTime.now().plusMinutes(CHALLENGE_TTL_MINUTES)
        );
    }

    private VerifyResult confirmSmsMock(DigitalAsset asset, String platform, String rawPhone,
                                        Map<String, String> payload, String challengeHash) {
        String phone = requireValidVnPhone(rawPhone);
        String carrier = detectVietnameseCarrier(phone);
        String code = firstNonBlank(payload, "code", "otp", "smsCode");
        if (code == null) {
            return new VerifyResult(false, VerificationProvider.SANDBOX,
                    "SMS verification code is required", Map.of());
        }
        if (challengeHash == null || challengeHash.isBlank()) {
            return new VerifyResult(false, VerificationProvider.SANDBOX,
                    "SMS verification challenge has not been started", Map.of());
        }

        UUID saltId = asset.getId() != null ? asset.getId() : new UUID(0L, 0L);
        boolean matched = otpHasher.matches(saltId, code, challengeHash);

        if (!matched) {
            return new VerifyResult(false, VerificationProvider.SANDBOX,
                    "Invalid SMS verification code", Map.of());
        }

        Map<String, String> evidence = new LinkedHashMap<>();
        evidence.put("platform", platform);
        evidence.put("phone", maskPhone(phone));
        evidence.put("carrier", carrier);
        evidence.put("mode", "SANDBOX");
        return new VerifyResult(true, VerificationProvider.SANDBOX,
                "E-wallet phone number verified via simulated SMS (Sandbox)", evidence);
    }

    // =========================================================
    // 2. PAYPAL_LOGIN
    // =========================================================

    private StartResult startPayPalLogin(DigitalAsset asset, String platform, String rawEmail) {
        requirePayPalCompatible(platform, rawEmail);
        String email = rawEmail.trim();

        String otp = otpGenerator.generate();
        UUID saltId = asset.getId() != null ? asset.getId() : new UUID(0L, 0L);
        String challengeHash = otpHasher.hash(saltId, otp);

        Map<String, String> clientData = new LinkedHashMap<>();
        clientData.put("platform", "PayPal");
        clientData.put("expectedEmail", email);
        clientData.put("paypalClientId", paypalClientId);
        clientData.put("sandboxCode", otp);
        clientData.put("mode", "SANDBOX");

        return new StartResult(
                VerificationProvider.SANDBOX,
                "Log in with PayPal Sandbox account " + email,
                challengeHash,
                clientData,
                LocalDateTime.now().plusMinutes(10)
        );
    }

    private VerifyResult confirmPayPalLogin(DigitalAsset asset, String platform, String rawEmail,
                                            Map<String, String> payload, String challengeHash) {
        requirePayPalCompatible(platform, rawEmail);
        String expectedEmail = rawEmail.trim().toLowerCase(Locale.ROOT);

        // Trường hợp 1: Client gửi accessToken từ PayPal OAuth Sandbox
        String accessToken = firstNonBlank(payload, "accessToken", "paypalAccessToken");
        if (accessToken != null) {
            try {
                String verifiedEmail = payPalEmailLookup.fetchVerifiedEmail(accessToken);
                if (verifiedEmail == null || !verifiedEmail.trim().equalsIgnoreCase(expectedEmail)) {
                    return new VerifyResult(false, VerificationProvider.SANDBOX,
                            "PayPal account email does not match declared wallet email", Map.of());
                }
                Map<String, String> evidence = new LinkedHashMap<>();
                evidence.put("platform", "PayPal");
                evidence.put("paypalEmail", expectedEmail);
                evidence.put("mode", "PAYPAL_SANDBOX_OAUTH");
                return new VerifyResult(true, VerificationProvider.SANDBOX,
                        "PayPal wallet verified via PayPal Sandbox OAuth", evidence);
            } catch (Exception e) {
                log.warn("Failed to verify PayPal access token: {}", e.getMessage());
                return new VerifyResult(false, VerificationProvider.SANDBOX,
                        "Could not verify PayPal Sandbox access token", Map.of());
            }
        }

        // Trường hợp 2: Xác nhận mã thử thách PayPal Sandbox
        String providedEmail = firstNonBlank(payload, "paypalEmail", "email");
        if (providedEmail != null && !providedEmail.equalsIgnoreCase(expectedEmail)) {
            return new VerifyResult(false, VerificationProvider.SANDBOX,
                    "PayPal email (" + providedEmail + ") does not match declared wallet email (" + expectedEmail + ")",
                    Map.of());
        }

        String code = firstNonBlank(payload, "code", "otp", "sandboxCode");
        if (code == null || challengeHash == null || challengeHash.isBlank()) {
            return new VerifyResult(false, VerificationProvider.SANDBOX,
                    "PayPal access token or sandbox verification code is required", Map.of());
        }

        UUID saltId = asset.getId() != null ? asset.getId() : new UUID(0L, 0L);
        boolean matched = otpHasher.matches(saltId, code, challengeHash);
        if (!matched) {
            return new VerifyResult(false, VerificationProvider.SANDBOX,
                    "Invalid PayPal sandbox verification code", Map.of());
        }

        Map<String, String> evidence = new LinkedHashMap<>();
        evidence.put("platform", "PayPal");
        evidence.put("paypalEmail", expectedEmail);
        evidence.put("mode", "SANDBOX");
        return new VerifyResult(true, VerificationProvider.SANDBOX,
                "PayPal wallet verified (Sandbox)", evidence);
    }

    private String fetchPayPalEmailFromSandbox(String accessToken) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api-m.sandbox.paypal.com/v1/identity/oauth2/userinfo?schema=paypalv1.1"))
                .timeout(Duration.ofMillis(3500))
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200 || resp.body() == null) {
            return null;
        }
        Matcher m = PAYPAL_EMAIL_JSON.matcher(resp.body());
        if (m.find()) {
            return m.group(1) != null ? m.group(1) : m.group(2);
        }
        return null;
    }

    private static void requirePayPalCompatible(String platform, String rawEmail) {
        if (!platform.isEmpty() && !platform.equalsIgnoreCase("PayPal") && !platform.equalsIgnoreCase("Other")) {
            throw new VaultException("PayPal login verification is only supported for PayPal wallets",
                    HttpStatus.BAD_REQUEST);
        }
        if (rawEmail == null || rawEmail.isBlank() || !EMAIL_PATTERN.matcher(rawEmail.trim()).matches()) {
            throw new VaultException("PayPal wallet requires a valid email address in usernameOrHandle",
                    HttpStatus.BAD_REQUEST);
        }
    }

    public static String requireValidVnPhone(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new VaultException("Phone number (usernameOrHandle) is required for e-wallet SMS verification",
                    HttpStatus.BAD_REQUEST);
        }
        String normalized = raw.replaceAll("[\\s.\\-()]", "");
        if (!VN_PHONE_PATTERN.matcher(normalized).matches()) {
            throw new VaultException(
                    "Invalid Vietnamese mobile phone number format (expected 03x/05x/07x/08x/09x or +84...)",
                    HttpStatus.BAD_REQUEST);
        }
        if (normalized.startsWith("+84")) {
            return "0" + normalized.substring(3);
        }
        return normalized;
    }

    public static String detectVietnameseCarrier(String normalizedLocalPhone) {
        if (normalizedLocalPhone == null || normalizedLocalPhone.length() < 3) {
            return "Unknown";
        }
        String prefix = normalizedLocalPhone.substring(0, 3);
        return switch (prefix) {
            case "086", "096", "097", "098", "032", "033", "034", "035", "036", "037", "038", "039" -> "Viettel";
            case "088", "091", "094", "081", "082", "083", "084", "085" -> "VinaPhone";
            case "089", "090", "093", "070", "076", "077", "078", "079" -> "MobiFone";
            case "092", "056", "058" -> "Vietnamobile";
            case "099", "059" -> "Gmobile";
            default -> "Vietnam Mobile";
        };
    }

    static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 3);
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
