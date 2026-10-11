package com.ltld.app.legacyvault.service.cryptoservice;

import org.junit.jupiter.api.Test;

import javax.crypto.AEADBadTagException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CryptoServiceImplTest {

    private static final String KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final String OTHER_KEY = Base64.getEncoder().encodeToString(new byte[32]);

    private final CryptoServiceImpl crypto = new CryptoServiceImpl(KEY);

    @Test
    void roundTrip_vietnameseText() throws Exception {
        String plain = "Mật khẩu Vietcombank: Tài khoản 0123 – ghi chú có dấu";
        assertThat(crypto.decrypt(crypto.encrypt(plain))).isEqualTo(plain);
    }

    @Test
    void roundTrip_bytes_andEmptyString() throws Exception {
        byte[] data = "%PDF-1.7 ...".getBytes(StandardCharsets.UTF_8);
        assertThat(crypto.decryptBytes(crypto.encryptBytes(data))).isEqualTo(data);
        assertThat(crypto.decrypt(crypto.encrypt(""))).isEmpty();
    }

    @Test
    void samePlaintext_producesDifferentCiphertext() throws Exception {
        assertThat(crypto.encrypt("same")).isNotEqualTo(crypto.encrypt("same"));
    }

    @Test
    void ciphertext_startsWithVersionByte() throws Exception {
        assertThat(crypto.encryptBytes(new byte[]{1, 2, 3})[0]).isEqualTo((byte) 1);
    }

    @Test
    void tamperedCiphertext_isRejected() throws Exception {
        byte[] ct = crypto.encryptBytes("secret".getBytes(StandardCharsets.UTF_8));
        ct[ct.length - 1] ^= 1;
        assertThatThrownBy(() -> crypto.decryptBytes(ct)).isInstanceOf(AEADBadTagException.class);
    }

    @Test
    void wrongKey_cannotDecrypt() throws Exception {
        String ct = crypto.encrypt("secret");
        assertThatThrownBy(() -> new CryptoServiceImpl(OTHER_KEY).decrypt(ct))
                .isInstanceOf(AEADBadTagException.class);
    }

    @Test
    void keyNot32Bytes_failsFast() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> new CryptoServiceImpl(shortKey))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("32 byte");
    }

    @Test
    void keyNotBase64_failsFast() {
        assertThatThrownBy(() -> new CryptoServiceImpl("1"))      // đúng giá trị đang có trong .env.example
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Base64");
    }

    @Test
    void nullInput_returnsNull() throws Exception {
        assertThat(crypto.encrypt(null)).isNull();
        assertThat(crypto.decrypt(null)).isNull();
        assertThat(crypto.encryptBytes(null)).isNull();
        assertThat(crypto.decryptBytes(null)).isNull();
    }

    @Test
    void garbageOrWrongVersion_isRejected() {
        assertThatThrownBy(() -> crypto.decryptBytes(new byte[]{9, 9, 9}))
                .isInstanceOf(GeneralSecurityException.class);
        byte[] fake = new byte[40];
        fake[0] = 2;                                              // version không hỗ trợ
        assertThatThrownBy(() -> crypto.decryptBytes(fake)).isInstanceOf(GeneralSecurityException.class);
    }
}