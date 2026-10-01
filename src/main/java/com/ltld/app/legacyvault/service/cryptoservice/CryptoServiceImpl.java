package com.ltld.app.legacyvault.service.cryptoservice;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Service
public class CryptoServiceImpl implements CryptoService {

    // Lấy khóa Master Key (32 ký tự = 256 bits). Nếu trong application.yml bạn không cài đặt, nó sẽ lấy chuỗi dự phòng phía sau.
    @Value("${app.security.crypto.master-key:12345678901234567890123456789012}")
    private String masterKey;

    private static final String ALGORITHM = "AES";

    @Override
    public String encrypt(String plainText) throws Exception {
        if (plainText == null) return null;
        // 1. Chuyển String thành byte -> 2. Mã hóa AES -> 3. Đổi mảng byte mã hóa ra Base64 String để lưu DB cho dễ
        byte[] encrypted = encryptBytes(plainText.getBytes());
        return Base64.getEncoder().encodeToString(encrypted);
    }

    @Override
    public String decrypt(String cipherText) throws Exception {
        if (cipherText == null) return null;
        // 1. Chuyển Base64 String về lại byte -> 2. Giải mã AES -> 3. Đổi về dạng text
        byte[] decrypted = decryptBytes(Base64.getDecoder().decode(cipherText));
        return new String(decrypted);
    }

    @Override
    public byte[] encryptBytes(byte[] plainBytes) throws Exception {
        if (plainBytes == null) return null;
        SecretKeySpec key = new SecretKeySpec(masterKey.getBytes(), ALGORITHM);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, key);
        return cipher.doFinal(plainBytes);
    }

    @Override
    public byte[] decryptBytes(byte[] cipherBytes) throws Exception {
        if (cipherBytes == null) return null;
        SecretKeySpec key = new SecretKeySpec(masterKey.getBytes(), ALGORITHM);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, key);
        return cipher.doFinal(cipherBytes);
    }
}

