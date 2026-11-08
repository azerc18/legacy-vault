package com.ltld.app.legacyvault.service.assetverificationservice;

import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.service.vaultservice.assetschema.FlatJson;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * C.5: dấu vân tay của tài sản = hash các trường "danh tính" (không gồm tên gọi, giá trị ước tính, ghi chú, bí mật).
 * Xác thực gắn với dấu vân tay lúc xác thực; đổi trường danh tính thì dấu vân tay đổi và xác thực không còn hiệu lực.
 */
public final class AssetFingerprint {

    static final Set<String> IDENTITY_KEYS = Set.of(
            "institutionName", "accountNumber", "accountId",
            "walletProvider", "blockchainNetwork", "walletAddress",
            "platform", "usernameOrHandle",
            "documentTitle", "issuer", "title");

    private AssetFingerprint() {
    }

    public static String compute(DigitalAsset asset) {
        StringBuilder sb = new StringBuilder();
        sb.append("type=").append(asset.getAssetType() == null ? "" : asset.getAssetType().name());
        Map<String, String> fields = FlatJson.decode(asset.getPublicFieldsJson());
        Map<String, String> identity = new TreeMap<>();   // thứ tự key cố định
        for (String key : IDENTITY_KEYS) {
            String value = fields.get(key);
            if (value != null && !value.isBlank()) {
                identity.put(key, value.trim());
            }
        }
        identity.forEach((k, v) -> sb.append('\n').append(k).append('=').append(v));
        return sha256Hex(sb.toString());
    }

    /** SHA-256 dạng hex (64 ký tự). Verifier cũng dùng hàm này để băm mã thử thách. */
    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
