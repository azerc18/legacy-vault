package com.ltld.app.legacyvault.service.impl;

import com.ltld.app.legacyvault.enums.SignatureMethod;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

public final class SignatureHasher {

    private SignatureHasher() {}

    public static String hash(UUID requestId, UUID verifierId, SignatureMethod method, String documentRef) {
        String raw = requestId + "|" + verifierId + "|" + method + "|" + documentRef;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}