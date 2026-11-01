package com.ltld.app.legacyvault.service.vaultservice.assetschema;

import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.TransferMechanism;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.exception.VaultException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("N3 – asset schema")
class AssetSchemaTest {

    private static Map<String, String> bank() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("institutionName", "Vietcombank");
        m.put("accountNumber", "0123456789");
        m.put("branchName", "HCM");
        m.put("estimatedValue", "1000000");
        m.put("currency", "VND");
        m.put("legalInstruction", "Bring ID and death certificate");
        return m;
    }

    @Test
    @DisplayName("mọi AssetType đều có schema, đúng cơ chế chuyển giao")
    void everyTypeHasSchema() {
        for (AssetType t : AssetType.values()) {
            assertThat(AssetSchemaRegistry.schemaOf(t)).as(t.name()).isNotNull();
        }
        assertThat(AssetSchemaRegistry.schemaOf(AssetType.BANK_ACCOUNT).mechanism())
                .isEqualTo(TransferMechanism.LEGAL_CLAIM_DOSSIER);
        assertThat(AssetSchemaRegistry.schemaOf(AssetType.CRYPTO_WALLET).mechanism())
                .isEqualTo(TransferMechanism.CRYPTOGRAPHIC_SPLIT_KEY);
        assertThat(AssetSchemaRegistry.schemaOf(AssetType.SOCIAL_MEDIA).mechanism())
                .isEqualTo(TransferMechanism.EXECUTOR_DISPOSITION);
        assertThat(AssetSchemaRegistry.schemaOf(AssetType.OTHER).mechanism()).isNull();
    }

    @Test
    @DisplayName("dữ liệu hợp lệ -> không lỗi")
    void validBank() {
        assertThatCode(() -> AssetFieldsValidator.validate(AssetType.BANK_ACCOUNT, bank())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("thiếu trường bắt buộc -> ASSET_FIELD_INVALID")
    void missingRequired() {
        Map<String, String> m = bank();
        m.remove("branchName");
        assertThatThrownBy(() -> AssetFieldsValidator.validate(AssetType.BANK_ACCOUNT, m))
                .isInstanceOf(VaultException.class)
                .extracting(e -> ((VaultException) e).getErrorCode()).isEqualTo(ErrorCode.ASSET_FIELD_INVALID);
    }

    @Test
    @DisplayName("sai định dạng số tài khoản / option / số tiền / trường lạ -> lỗi")
    void invalidValues() {
        Map<String, String> badAcc = bank();
        badAcc.put("accountNumber", "12ab");
        Map<String, String> badCur = bank();
        badCur.put("currency", "GOLD");
        Map<String, String> badNum = bank();
        badNum.put("estimatedValue", "-5");
        Map<String, String> unknown = bank();
        unknown.put("hack", "x");
        for (Map<String, String> m : java.util.List.of(badAcc, badCur, badNum, unknown)) {
            assertThatThrownBy(() -> AssetFieldsValidator.validate(AssetType.BANK_ACCOUNT, m))
                    .isInstanceOf(VaultException.class);
        }
    }

    @Test
    @DisplayName("MXH bắt buộc chọn di nguyện (wish)")
    void socialNeedsWish() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("platform", "Facebook");
        m.put("usernameOrHandle", "duy");
        assertThatThrownBy(() -> AssetFieldsValidator.validate(AssetType.SOCIAL_MEDIA, m))
                .isInstanceOf(VaultException.class);
        m.put("wish", "MEMORIALIZE");
        assertThatCode(() -> AssetFieldsValidator.validate(AssetType.SOCIAL_MEDIA, m)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("m1: seed phrase phải đủ 12 hoặc 24 từ; ví cần seed phrase hoặc private key")
    void walletSeedPhrase() {
        Map<String, String> w = new LinkedHashMap<>();
        w.put("walletProvider", "Ledger");
        w.put("blockchainNetwork", "Bitcoin");
        w.put("walletAddress", "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh");
        assertThatThrownBy(() -> AssetFieldsValidator.validate(AssetType.CRYPTO_WALLET, w))
                .isInstanceOf(VaultException.class);
        w.put("seedPhrase", "only three words");
        assertThatThrownBy(() -> AssetFieldsValidator.validate(AssetType.CRYPTO_WALLET, w))
                .isInstanceOf(VaultException.class);
        w.put("seedPhrase", "a b c d e f g h i j k l");
        assertThatCode(() -> AssetFieldsValidator.validate(AssetType.CRYPTO_WALLET, w)).doesNotThrowAnyException();
        w.put("seedPhrase", "a b c d e f g h i j k l m n o p q r s t u v w x");
        assertThatCode(() -> AssetFieldsValidator.validate(AssetType.CRYPTO_WALLET, w)).doesNotThrowAnyException();
        w.remove("seedPhrase");
        w.put("privateKey", "0xabc");
        assertThatCode(() -> AssetFieldsValidator.validate(AssetType.CRYPTO_WALLET, w)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("FlatJson: chuỗi hỏng ném IllegalArgumentException")
    void flatJsonRejectsMalformed() {
        for (String bad : new String[]{"{", "{\"a\":1}", "abc", "{\"a\":\"b\""}) {
            assertThatThrownBy(() -> FlatJson.decode(bad)).isInstanceOf(RuntimeException.class);
        }
    }

    @Test
    @DisplayName("split tách trường bí mật khỏi trường công khai")
    void splitSecrets() {
        Map<String, String> m = bank();
        m.put("pinOrPasswordNote", "1234");
        AssetSchemaRegistry.Split s = AssetSchemaRegistry.split(AssetType.BANK_ACCOUNT, m);
        assertThat(s.secretFields()).containsOnlyKeys("pinOrPasswordNote");
        assertThat(s.publicFields()).doesNotContainKey("pinOrPasswordNote").containsKey("accountNumber");
    }

    @Test
    @DisplayName("FlatJson encode/decode giữ nguyên ký tự đặc biệt")
    void flatJsonRoundTrip() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("a", "he said \"hi\"\nnew line \\ tab\t");
        m.put("b", "tiếng Việt ✓");
        assertThat(FlatJson.decode(FlatJson.encode(m))).isEqualTo(m);
        assertThat(FlatJson.decode("{}")).isEmpty();
        assertThat(FlatJson.decode(FlatJson.encode(Map.of()))).isEmpty();
    }
}
