package com.ltld.app.legacyvault.utility;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class RefreshTokenUtil {
    private final int TOKEN_BYTE_LENGTH = 64;
    private final String HASH_ALGORITHM = "SHA-256";
    private final SecureRandom secureRandom = new SecureRandom();

    public String generateRawToken(){
        byte[] randomBytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    public String hashToken(String rawToken){
        if(rawToken == null || rawToken.isBlank()){
            throw new IllegalArgumentException("Raw token must not be blank");
        }

        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();

            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }

            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error hashing refresh token", e);
        }
    }
}
