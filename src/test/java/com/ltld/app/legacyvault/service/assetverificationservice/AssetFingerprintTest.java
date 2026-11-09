package com.ltld.app.legacyvault.service.assetverificationservice;

import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.enums.AssetType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AssetFingerprint – C.5 dấu vân tay tài sản")
class AssetFingerprintTest {

    private static DigitalAsset asset(AssetType type, String publicJson) {
        return DigitalAsset.builder().assetType(type).assetName("MB Bank").publicFieldsJson(publicJson).build();
    }

    private static final String BASE =
            "{\"institutionName\":\"MB Bank\",\"accountNumber\":\"0123456789\",\"estimatedValue\":\"1000\"}";

    @Test
    @DisplayName("Cùng dữ liệu → cùng dấu vân tay (64 ký tự hex)")
    void sameData_sameFingerprint() {
        String a = AssetFingerprint.compute(asset(AssetType.BANK_ACCOUNT, BASE));
        String b = AssetFingerprint.compute(asset(AssetType.BANK_ACCOUNT, BASE));
        assertThat(a).isEqualTo(b).hasSize(64);
    }

    @Test
    @DisplayName("Đổi trường danh tính (số tài khoản) → dấu vân tay đổi")
    void identityFieldChanged_differentFingerprint() {
        String before = AssetFingerprint.compute(asset(AssetType.BANK_ACCOUNT, BASE));
        String after = AssetFingerprint.compute(asset(AssetType.BANK_ACCOUNT,
                "{\"institutionName\":\"MB Bank\",\"accountNumber\":\"9999999999\",\"estimatedValue\":\"1000\"}"));
        assertThat(after).isNotEqualTo(before);
    }

    @Test
    @DisplayName("Đổi trường không phải danh tính (giá trị ước tính) → dấu vân tay giữ nguyên")
    void nonIdentityFieldChanged_sameFingerprint() {
        String before = AssetFingerprint.compute(asset(AssetType.BANK_ACCOUNT, BASE));
        String after = AssetFingerprint.compute(asset(AssetType.BANK_ACCOUNT,
                "{\"institutionName\":\"MB Bank\",\"accountNumber\":\"0123456789\",\"estimatedValue\":\"5000\","
                        + "\"legalInstruction\":\"abc\"}"));
        assertThat(after).isEqualTo(before);
    }

    @Test
    @DisplayName("Đổi tên gọi tài sản → dấu vân tay giữ nguyên")
    void assetNameChanged_sameFingerprint() {
        DigitalAsset renamed = asset(AssetType.BANK_ACCOUNT, BASE);
        renamed.setAssetName("Tài khoản lương");
        assertThat(AssetFingerprint.compute(renamed))
                .isEqualTo(AssetFingerprint.compute(asset(AssetType.BANK_ACCOUNT, BASE)));
    }

    @Test
    @DisplayName("Thứ tự key trong JSON không ảnh hưởng")
    void keyOrderIrrelevant() {
        String reordered = "{\"estimatedValue\":\"1000\",\"accountNumber\":\"0123456789\",\"institutionName\":\"MB Bank\"}";
        assertThat(AssetFingerprint.compute(asset(AssetType.BANK_ACCOUNT, reordered)))
                .isEqualTo(AssetFingerprint.compute(asset(AssetType.BANK_ACCOUNT, BASE)));
    }

    @Test
    @DisplayName("Khác loại tài sản → dấu vân tay khác")
    void differentType_differentFingerprint() {
        assertThat(AssetFingerprint.compute(asset(AssetType.BANK_ACCOUNT, BASE)))
                .isNotEqualTo(AssetFingerprint.compute(asset(AssetType.EXCHANGE_ACCOUNT, BASE)));
    }

    @Test
    @DisplayName("Tài sản kiểu cũ (không có public fields) vẫn tính được")
    void nullPublicFields_ok() {
        assertThat(AssetFingerprint.compute(asset(AssetType.OTHER, null))).hasSize(64);
    }

    @Test
    @DisplayName("sha256Hex đúng giá trị chuẩn của \"abc\"")
    void sha256Hex_knownVector() {
        assertThat(AssetFingerprint.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    @DisplayName("Đổi tên chủ tài khoản hoặc email đăng ký sàn → dấu vân tay đổi")
    void holderNameOrRegisteredEmailChanged_differentFingerprint() {
        String bankA = "{\"institutionName\":\"MB Bank\",\"accountNumber\":\"0123456789\",\"accountHolderName\":\"Nguyen Van A\"}";
        String bankB = "{\"institutionName\":\"MB Bank\",\"accountNumber\":\"0123456789\",\"accountHolderName\":\"Nguyen Van B\"}";
        assertThat(AssetFingerprint.compute(asset(AssetType.BANK_ACCOUNT, bankA)))
                .isNotEqualTo(AssetFingerprint.compute(asset(AssetType.BANK_ACCOUNT, bankB)));

        String exA = "{\"institutionName\":\"Binance\",\"accountId\":\"u123\",\"registeredEmail\":\"a@x.com\"}";
        String exB = "{\"institutionName\":\"Binance\",\"accountId\":\"u123\",\"registeredEmail\":\"b@x.com\"}";
        assertThat(AssetFingerprint.compute(asset(AssetType.EXCHANGE_ACCOUNT, exA)))
                .isNotEqualTo(AssetFingerprint.compute(asset(AssetType.EXCHANGE_ACCOUNT, exB)));
    }
}
