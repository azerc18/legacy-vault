package com.ltld.app.legacyvault.service.beneficiaryservice;

import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.enums.AssetType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecretDownloadFormatTest {

    private static DigitalAsset asset(AssetType type) {
        DigitalAsset a = new DigitalAsset();
        a.setAssetType(type);
        return a;
    }

    @Test
    void schemaSecretJson_isSplitIntoLabeledLines() {
        String out = BeneficiaryServiceImpl.formatSecretForDownload(asset(AssetType.BANK_ACCOUNT),
                "{\"pinOrPasswordNote\":\"098765\"}");
        assertThat(out).isEqualTo("\n  PIN / password note: 098765");
    }

    @Test
    void multipleFields_keepOrder_andSkipBlank() {
        String out = BeneficiaryServiceImpl.formatSecretForDownload(asset(AssetType.CRYPTO_WALLET),
                "{\"seedPhrase\":\"a b c\",\"privateKey\":\"\",\"passphrase\":\"pp\"}");
        assertThat(out).isEqualTo("\n  Seed phrase (12 or 24 words): a b c\n  Passphrase: pp");
    }

    @Test
    void plainTextSecret_isKeptAsIs() {
        assertThat(BeneficiaryServiceImpl.formatSecretForDownload(asset(AssetType.OTHER), "my secret"))
                .isEqualTo(" my secret");
        assertThat(BeneficiaryServiceImpl.formatSecretForDownload(asset(AssetType.OTHER), "{not json"))
                .isEqualTo(" {not json");
        assertThat(BeneficiaryServiceImpl.formatSecretForDownload(asset(AssetType.OTHER), null)).isEqualTo(" (none)");
    }

    @Test
    void emptyJson_orAllBlank_isNone() {
        assertThat(BeneficiaryServiceImpl.formatSecretForDownload(asset(AssetType.DOCUMENT), "{}")).isEqualTo(" (none)");
        assertThat(BeneficiaryServiceImpl.formatSecretForDownload(asset(AssetType.BANK_ACCOUNT), "{\"pinOrPasswordNote\":\"\"}"))
                .isEqualTo(" (none)");
    }

    @Test
    void unknownKey_fallsBackToKeyAsLabel() {
        assertThat(BeneficiaryServiceImpl.formatSecretForDownload(asset(AssetType.BANK_ACCOUNT), "{\"legacyPin\":\"1\"}"))
                .isEqualTo("\n  legacyPin: 1");
    }
}
