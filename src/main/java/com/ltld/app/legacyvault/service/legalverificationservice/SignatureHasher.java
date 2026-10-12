package com.ltld.app.legacyvault.service.legalverificationservice;

import com.ltld.app.legacyvault.enums.SignatureMethod;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

// Package-private utility class theo yêu cầu P1 & N7
class SignatureHasher {

    private static final String HASH_ALGORITHM = "SHA-256";

    public static String hash(UUID requestId, UUID verifierId, SignatureMethod method, String documentRef) {
        String payload = String.format("%s|%s|%s|%s", requestId, verifierId, method, documentRef);
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
