package com.ltld.app.legacyvault.service.vaultservice.assetschema;

import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.exception.VaultException;

import java.util.Map;
import java.util.regex.Pattern;

/** N3: kiểm các trường người dùng gửi theo schema của loại tài sản. Lỗi -> ASSET_FIELD_INVALID. */
public final class AssetFieldsValidator {

    private static final Pattern WHOLE_NUMBER = Pattern.compile("^(0|[1-9]\\d{0,14})$");

    static final int MAX_VALUE_LENGTH = 2000;

    private AssetFieldsValidator() {
    }

    /**
     * @param merged toàn bộ giá trị (công khai + bí mật) sau khi gộp với giá trị cũ khi sửa
     */
    public static void validate(AssetType type, Map<String, String> merged) {
        AssetSchema schema = AssetSchemaRegistry.schemaOf(type);
        for (String key : merged.keySet()) {
            if (schema.fields().stream().noneMatch(f -> f.key().equals(key))) {
                throw invalid("Unknown field '" + key + "' for " + type);
            }
        }
        // Ví crypto phải có seed phrase hoặc private key (tính cả giá trị cũ đã gộp khi sửa)
        if (type == AssetType.CRYPTO_WALLET && isBlank(merged.get("seedPhrase")) && isBlank(merged.get("privateKey"))) {
            throw invalid("Seed phrase or private key is required");
        }
        for (AssetFieldSpec spec : schema.fields()) {
            String value = merged.get(spec.key());
            if (value == null || value.isBlank()) {
                if (spec.required()) {
                    throw invalid(spec.label() + " is required");
                }
                continue;
            }
            if (value.length() > MAX_VALUE_LENGTH) {
                throw invalid(spec.label() + " is too long");
            }
            switch (spec.type()) {
                case SELECT -> {
                    if (!spec.options().contains(value)) {
                        throw invalid(spec.label() + " has an invalid option");
                    }
                }
                case NUMBER -> {
                    // Số nguyên không âm, chỉ gồm chữ số, không có số 0 thừa ở đầu và tối đa 15 chữ số (khớp luật ở FE:
                    // AssetFields.fieldError). Nhận "0" (tài sản cũ có giá trị 0 vẫn sửa được); không nhận "000000",
                    // "0123", thập phân, dấu âm hay ký hiệu khoa học như 1e5
                    if (!WHOLE_NUMBER.matcher(value).matches()) {
                        throw invalid(spec.label() + " must be a whole number (digits only, no leading zeros, max 15 digits)");
                    }
                }
                default -> {
                    if (spec.pattern() != null && !Pattern.matches(spec.pattern(), value)) {
                        throw invalid(spec.label() + " has an invalid format");
                    }
                }
            }
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static VaultException invalid(String message) {
        return new VaultException(ErrorCode.ASSET_FIELD_INVALID, message);
    }
}
