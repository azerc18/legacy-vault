package com.ltld.app.legacyvault.service.cryptoservice;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class CryptoServiceImpl implements CryptoService {

    private static final byte VERSION = 1;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;
    private static final int HEADER_LEN = 1 + IV_LEN;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    // Không có giá trị mặc định: thiếu key thì app dừng ngay khi khởi động (fail-fast)
    public CryptoServiceImpl(@Value("${app.security.crypto.master-key}") String base64Key) {
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("app.security.crypto.master-key phải là chuỗi Base64", e);
        }
        if (raw.length != 32) {
            throw new IllegalStateException("Master key phải đúng 32 byte (AES-256), hiện là " + raw.length);
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    @Override
    public String encrypt(String plainText) throws Exception {
        if (plainText == null) return null;
        return Base64.getEncoder().encodeToString(encryptBytes(plainText.getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public String decrypt(String cipherText) throws Exception {
        if (cipherText == null) return null;
        return new String(decryptBytes(Base64.getDecoder().decode(cipherText)), StandardCharsets.UTF_8);
    }

    @Override
    public byte[] encryptBytes(byte[] plainBytes) throws Exception {
        if (plainBytes == null) return null;
        byte[] iv = new byte[IV_LEN];
        random.nextBytes(iv);                                   // IV mới cho MỖI lần mã hóa

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
        byte[] ct = cipher.doFinal(plainBytes);

        return ByteBuffer.allocate(HEADER_LEN + ct.length)
                .put(VERSION).put(iv).put(ct)
                .array();
    }

    @Override
    public byte[] decryptBytes(byte[] data) throws Exception {
        if (data == null) return null;
        if (data.length < HEADER_LEN + TAG_BITS / 8 || data[0] != VERSION) {
            throw new GeneralSecurityException("Bản mã không đúng định dạng hoặc phiên bản");
        }
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, data, 1, IV_LEN));
        // Sai key hoặc dữ liệu bị sửa thì doFinal ném AEADBadTagException
        return cipher.doFinal(data, HEADER_LEN, data.length - HEADER_LEN);
    }
}

