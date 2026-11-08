package com.ltld.app.legacyvault.utility;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// #19: OTP băm bằng HMAC với khóa ngoài DB, vẫn nhận hash SHA-256 cũ
class OtpHasherTest {

    private static final String MASTER = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final String OTHER_MASTER = Base64.getEncoder().encodeToString(new byte[32]);
    private final UUID id = UUID.randomUUID();

    @Test
    void hash_roundTrip_matches() {
        OtpHasher hasher = new OtpHasher("", MASTER);
        String stored = hasher.hash(id, "123456");
        assertThat(hasher.matches(id, "123456", stored)).isTrue();
        assertThat(hasher.matches(id, "654321", stored)).isFalse();
        assertThat(hasher.matches(UUID.randomUUID(), "123456", stored)).isFalse();
    }

    @Test
    void hash_isNotPlainSha256_soDbLeakAloneCannotBruteForce() throws Exception {
        String stored = new OtpHasher("", MASTER).hash(id, "123456");
        assertThat(stored).isNotEqualTo(sha256(id + ":123456"));
    }

    @Test
    void hash_dependsOnSecretKey() {
        String a = new OtpHasher("", MASTER).hash(id, "123456");
        String b = new OtpHasher("", OTHER_MASTER).hash(id, "123456");
        assertThat(a).isNotEqualTo(b);
        assertThat(new OtpHasher("", OTHER_MASTER).matches(id, "123456", a)).isFalse();
    }

    @Test
    void hash_sameKeyIsStableAcrossInstances() {
        assertThat(new OtpHasher("", MASTER).hash(id, "123456"))
                .isEqualTo(new OtpHasher("", MASTER).hash(id, "123456"));
    }

    @Test
    void explicitHmacKey_overridesDerivedKey() {
        String explicit = Base64.getEncoder().encodeToString("k".repeat(32).getBytes(StandardCharsets.UTF_8));
        assertThat(new OtpHasher(explicit, MASTER).hash(id, "123456"))
                .isNotEqualTo(new OtpHasher("", MASTER).hash(id, "123456"));
    }

    @Test
    void explicitHmacKey_tooShort_failsFast() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> new OtpHasher(shortKey, MASTER)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void matches_acceptsLegacySha256Hash() throws Exception {
        // Mã dự phòng 2FA / OTP lưu trước khi đổi sang HMAC
        String legacy = sha256(id + ":123456");
        assertThat(new OtpHasher("", MASTER).matches(id, "123456", legacy)).isTrue();
        assertThat(new OtpHasher("", MASTER).matches(id, "000000", legacy)).isFalse();
    }

    @Test
    void matches_nullOrBlank_returnsFalse() {
        OtpHasher hasher = new OtpHasher("", MASTER);
        assertThat(hasher.matches(id, null, "x")).isFalse();
        assertThat(hasher.matches(id, " ", "x")).isFalse();
        assertThat(hasher.matches(id, "123456", null)).isFalse();
    }

    private static String sha256(String s) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
    }
}
