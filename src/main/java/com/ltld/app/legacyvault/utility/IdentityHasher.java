package com.ltld.app.legacyvault.utility;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * HMAC-SHA256 cho số CCCD / CMND, dùng để tra trùng mà không cần giải mã.
 *
 * <p>Khoá RIÊNG, không dùng chung với OTP: đổi khoá OTP (mã chỉ sống vài phút) không được làm hỏng hash CCCD
 * đã lưu lâu dài. Khoá lấy từ {@code app.security.identity-hmac-key} (Base64, ≥ 32 byte); để trống thì suy ra
 * từ {@code CRYPTO_MASTER_KEY} với nhãn {@code legacyvault/identity-hmac/v1}.
 *
 * <p>ĐỔI KHOÁ NÀY (hoặc master key khi không đặt khoá riêng) thì phải tính lại cột {@code *_hash} của bảng
 * {@code owner_identities} từ bản mã hoá, nếu không kiểm tra trùng CCCD sẽ mất tác dụng.
 */
@Component
public class IdentityHasher {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final byte[] DERIVE_LABEL = "legacyvault/identity-hmac/v1".getBytes(StandardCharsets.UTF_8);
    private static final int MIN_KEY_BYTES = 32;

    private final SecretKeySpec key;

    @Autowired
    public IdentityHasher(@Value("${app.security.identity-hmac-key:}") String hmacKey,
                          @Value("${app.security.crypto.master-key}") String masterKey) {
        this.key = new SecretKeySpec(resolveKey(hmacKey, masterKey), HMAC_ALGORITHM);
    }

    /** Cho unit test không có Spring: khoá ngẫu nhiên, chỉ dùng trong một instance. */
    public IdentityHasher() {
        byte[] random = new byte[MIN_KEY_BYTES];
        new SecureRandom().nextBytes(random);
        this.key = new SecretKeySpec(random, HMAC_ALGORITHM);
    }

    /** kind tách không gian hash giữa các loại giấy tờ (vd. "citizen-id", "old-id"). */
    public String hash(String kind, String digits) {
        if (kind == null || digits == null || digits.isBlank()) {
            throw new IllegalArgumentException("kind and digits must not be blank");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal((kind + ":" + digits).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot hash identity number", e);
        }
    }

    private static byte[] resolveKey(String hmacKey, String masterKey) {
        if (hmacKey != null && !hmacKey.isBlank()) {
            byte[] raw = decode(hmacKey, "app.security.identity-hmac-key");
            if (raw.length < MIN_KEY_BYTES) {
                throw new IllegalStateException("app.security.identity-hmac-key must be at least 32 bytes");
            }
            return raw;
        }
        if (masterKey == null || masterKey.isBlank()) {
            throw new IllegalStateException("app.security.crypto.master-key is required to derive the identity HMAC key");
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(decode(masterKey, "app.security.crypto.master-key"), HMAC_ALGORITHM));
            return mac.doFinal(DERIVE_LABEL);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot derive the identity HMAC key", e);
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
