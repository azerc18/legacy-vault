package com.ltld.app.legacyvault.utility;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * Băm OTP trước khi lưu DB. Dùng id phiên làm "muối" để hash không dùng chung giữa các phiên.
 * Lưu ý: OTP chỉ có 6 chữ số nên SHA-256 thuần không chống được dò ngược nếu DB bị lộ.
 * Khi cần chặt hơn, đổi thân hàm hash() sang HMAC với khóa bí mật nằm ngoài DB; chỗ khác không phải đổi.
 */
@Component
public class OtpHasher {

    private static final String HASH_ALGORITHM = "SHA-256";

    public String hash(UUID verificationId, String otp) {
        if (verificationId == null || otp == null || otp.isBlank()) {
            throw new IllegalArgumentException("verificationId and otp must not be blank");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hash = digest.digest((verificationId + ":" + otp).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    // So sánh theo thời gian không đổi để tránh timing attack
    public boolean matches(UUID verificationId, String otp, String storedHash) {
        if (otp == null || otp.isBlank() || storedHash == null) {
            return false;
        }
        byte[] expected = hash(verificationId, otp).getBytes(StandardCharsets.UTF_8);
        byte[] actual = storedHash.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }
}