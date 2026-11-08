package com.ltld.app.legacyvault.utility;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Băm OTP trước khi lưu DB. Dùng id (phiên / user) làm "muối" để hash không dùng chung giữa các phiên.
 *
 * <p>#19: hash bằng HMAC-SHA256 với khóa bí mật NẰM NGOÀI DB. OTP chỉ có 6 chữ số nên SHA-256 thuần dò ngược
 * được trong vài giây nếu DB bị lộ; có HMAC thì phải có thêm khóa. Khóa lấy từ {@code app.security.otp-hmac-key}
 * (Base64, ≥ 32 byte); để trống thì suy ra từ {@code CRYPTO_MASTER_KEY} (HMAC của master key với nhãn cố định),
 * nên không cần thêm biến môi trường khi deploy.
 *
 * <p>Tương thích ngược: {@link #matches} vẫn chấp nhận hash SHA-256 cũ, để mã dự phòng 2FA đã lưu (MfaBackupCode)
 * và OTP vừa gửi trước lúc deploy vẫn dùng được. Hash mới luôn là HMAC.
 */
@Component
public class OtpHasher {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String LEGACY_ALGORITHM = "SHA-256";
    private static final byte[] DERIVE_LABEL = "legacyvault/otp-hmac/v1".getBytes(StandardCharsets.UTF_8);
    private static final int MIN_KEY_BYTES = 32;

    private final SecretKeySpec key;

    @Autowired
    public OtpHasher(@Value("${app.security.otp-hmac-key:}") String hmacKey,
                     @Value("${app.security.crypto.master-key}") String masterKey) {
        this.key = new SecretKeySpec(resolveKey(hmacKey, masterKey), HMAC_ALGORITHM);
    }

    /** Cho unit test không có Spring: khóa ngẫu nhiên, chỉ dùng trong một instance. */
    public OtpHasher() {
        byte[] random = new byte[MIN_KEY_BYTES];
        new SecureRandom().nextBytes(random);
        this.key = new SecretKeySpec(random, HMAC_ALGORITHM);
    }

    public String hash(UUID verificationId, String otp) {
        requireInput(verificationId, otp);
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(message(verificationId, otp)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }

    // So sánh theo thời gian không đổi để tránh timing attack
    public boolean matches(UUID verificationId, String otp, String storedHash) {
        if (verificationId == null || otp == null || otp.isBlank() || storedHash == null) {
            return false;
        }
        byte[] actual = storedHash.getBytes(StandardCharsets.UTF_8);
        boolean hmacMatch = MessageDigest.isEqual(hash(verificationId, otp).getBytes(StandardCharsets.UTF_8), actual);
        boolean legacyMatch = MessageDigest.isEqual(legacyHash(verificationId, otp).getBytes(StandardCharsets.UTF_8), actual);
        return hmacMatch | legacyMatch;   // không dùng || để luôn tính cả hai (thời gian không đổi)
    }

    private static String legacyHash(UUID verificationId, String otp) {
        try {
            MessageDigest digest = MessageDigest.getInstance(LEGACY_ALGORITHM);
            return HexFormat.of().formatHex(digest.digest(message(verificationId, otp)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static byte[] message(UUID verificationId, String otp) {
        return (verificationId + ":" + otp).getBytes(StandardCharsets.UTF_8);
    }

    private static void requireInput(UUID verificationId, String otp) {
        if (verificationId == null || otp == null || otp.isBlank()) {
            throw new IllegalArgumentException("verificationId and otp must not be blank");
        }
    }

    private static byte[] resolveKey(String hmacKey, String masterKey) {
        if (hmacKey != null && !hmacKey.isBlank()) {
            byte[] raw = decode(hmacKey, "app.security.otp-hmac-key");
            if (raw.length < MIN_KEY_BYTES) {
                throw new IllegalStateException("app.security.otp-hmac-key must be at least 32 bytes");
            }
            return raw;
        }
        if (masterKey == null || masterKey.isBlank()) {
            throw new IllegalStateException("app.security.crypto.master-key is required to derive the OTP HMAC key");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(decode(masterKey, "app.security.crypto.master-key"), HMAC_ALGORITHM));
            return mac.doFinal(DERIVE_LABEL);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot derive the OTP HMAC key", e);
        }
    }

    private static byte[] decode(String base64, String property) {
        try {
            return Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(property + " must be a Base64 string", e);
        }
    }
}
