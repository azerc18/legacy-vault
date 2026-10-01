package com.ltld.app.legacyvault.service.cryptoservice;

public interface CryptoService {
    // Dùng để mã hóa/giải mã chuỗi văn bản (như password, ghi chú...)
    String encrypt(String plainText) throws Exception;
    String decrypt(String cipherText) throws Exception;

    // Dùng để mã hóa/giải mã file dữ liệu (phục vụ cho yêu cầu FR-04 sau này)
    byte[] encryptBytes(byte[] plainBytes) throws Exception;
    byte[] decryptBytes(byte[] cipherBytes) throws Exception;
}
