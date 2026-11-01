package com.ltld.app.legacyvault.service.vaultservice.assetschema;

import java.util.List;

/**
 * N3: mô tả một trường của tài sản.
 * sensitive = true -> trường bí mật: mã hóa khi lưu, Executor không bao giờ nhận được.
 */
public record AssetFieldSpec(String key, String label, AssetFieldType type, boolean required,
                             boolean sensitive, String pattern, List<String> options) {

    static AssetFieldSpec text(String key, String label, boolean required) {
        return new AssetFieldSpec(key, label, AssetFieldType.TEXT, required, false, null, List.of());
    }

    static AssetFieldSpec text(String key, String label, boolean required, String pattern) {
        return new AssetFieldSpec(key, label, AssetFieldType.TEXT, required, false, pattern, List.of());
    }

    static AssetFieldSpec textarea(String key, String label, boolean required) {
        return new AssetFieldSpec(key, label, AssetFieldType.TEXTAREA, required, false, null, List.of());
    }

    static AssetFieldSpec number(String key, String label, boolean required) {
        return new AssetFieldSpec(key, label, AssetFieldType.NUMBER, required, false, null, List.of());
    }

    static AssetFieldSpec select(String key, String label, boolean required, List<String> options) {
        return new AssetFieldSpec(key, label, AssetFieldType.SELECT, required, false, null, options);
    }

    static AssetFieldSpec secret(String key, String label, boolean required) {
        return new AssetFieldSpec(key, label, AssetFieldType.SECRET, required, true, null, List.of());
    }
}
