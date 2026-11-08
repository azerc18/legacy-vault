package com.ltld.app.legacyvault.utility;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** SHA-256 dạng hex thường (64 ký tự). */
public final class Sha256 {

    private Sha256() {
    }

    public static String hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    public static String hex(String text) {
        return hex(text.getBytes(StandardCharsets.UTF_8));
    }
}
