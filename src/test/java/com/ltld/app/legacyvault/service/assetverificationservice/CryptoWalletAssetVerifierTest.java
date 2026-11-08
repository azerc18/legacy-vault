package com.ltld.app.legacyvault.service.assetverificationservice;

import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.AssetVerificationMethod;
import com.ltld.app.legacyvault.enums.VerificationProvider;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import com.ltld.app.legacyvault.utility.OtpGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CryptoWalletAssetVerifier – C.3.5 xác thực tài sản CRYPTO_WALLET")
class CryptoWalletAssetVerifierTest {

    private static final String TEST_PRIV_KEY = "0x4c0883a69102937d6231471b5dbb6204fe5129617082792ae468d01a3f362318";
    private static final String TEST_ETH_ADDRESS = CryptoVerificationUtils.deriveAddressFromPrivateKey(TEST_PRIV_KEY);
    private static final String VALID_BIP39_12 =
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about";

    @Mock private CryptoService cryptoService;
    @Mock private OtpGenerator otpGenerator;

    private CryptoWalletAssetVerifier verifier;
    private UUID ownerId;
    private UUID assetId;

    @BeforeEach
    void setUp() {
        verifier = new CryptoWalletAssetVerifier(cryptoService, otpGenerator);
        ownerId = UUID.randomUUID();
        assetId = UUID.randomUUID();
    }

    private DigitalAsset cryptoAsset(String network, String address, String encryptedSecret) {
        return DigitalAsset.builder()
                .id(assetId)
                .assetType(AssetType.CRYPTO_WALLET)
                .assetName("Main Wallet")
                .publicFieldsJson("{\"walletProvider\":\"MetaMask\",\"blockchainNetwork\":\"" + network
                        + "\",\"walletAddress\":\"" + address + "\"}")
                .encryptedSecret(encryptedSecret)
                .build();
    }

    @Test
    @DisplayName("ADDRESS_CHECKSUM: địa chỉ Ethereum EIP-55 và Bitcoin Bech32 đúng chuẩn → REAL success")
    void addressChecksum_validEip55AndBitcoin_success() {
        DigitalAsset ethAsset = cryptoAsset("Ethereum", TEST_ETH_ADDRESS, null);
        AssetVerifier.VerifyResult ethResult = verifier.confirm(
                ethAsset, AssetVerificationMethod.ADDRESS_CHECKSUM, Map.of(), ownerId, null);
        assertThat(ethResult.success()).isTrue();
        assertThat(ethResult.provider()).isEqualTo(VerificationProvider.REAL);
        assertThat(ethResult.evidence()).containsEntry("checksumScheme", "EIP-55 (Keccak-256)");

        DigitalAsset btcAsset = cryptoAsset("Bitcoin", "bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4", null);
        AssetVerifier.VerifyResult btcResult = verifier.confirm(
                btcAsset, AssetVerificationMethod.ADDRESS_CHECKSUM, Map.of(), ownerId, null);
        assertThat(btcResult.success()).isTrue();
        assertThat(btcResult.provider()).isEqualTo(VerificationProvider.REAL);
    }

    @Test
    @DisplayName("ADDRESS_CHECKSUM: địa chỉ sai checksum bị từ chối")
    void addressChecksum_corruptedAddress_fails() {
        DigitalAsset badEth = cryptoAsset("Ethereum", TEST_ETH_ADDRESS.toLowerCase(), null);
        assertThat(verifier.confirm(badEth, AssetVerificationMethod.ADDRESS_CHECKSUM, Map.of(), ownerId, null)
                .success()).isFalse();

        DigitalAsset badBtc = cryptoAsset("Bitcoin", "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNb", null);
        assertThat(verifier.confirm(badBtc, AssetVerificationMethod.ADDRESS_CHECKSUM, Map.of(), ownerId, null)
                .success()).isFalse();
    }

    @Test
    @DisplayName("BIP39_CHECK: giải mã seedPhrase từ tài sản, kiểm đúng wordlist + checksum và không lộ từ khóa trong evidence")
    void bip39Check_fromEncryptedAssetSecret_successWithoutLeakingWords() throws Exception {
        DigitalAsset asset = cryptoAsset("Ethereum", TEST_ETH_ADDRESS, "enc-secret");
        when(cryptoService.decrypt("enc-secret")).thenReturn("{\"seedPhrase\":\"" + VALID_BIP39_12 + "\"}");

        AssetVerifier.VerifyResult result = verifier.confirm(
                asset, AssetVerificationMethod.BIP39_CHECK, Map.of(), ownerId, null);

        assertThat(result.success()).isTrue();
        assertThat(result.provider()).isEqualTo(VerificationProvider.REAL);
        assertThat(result.evidence())
                .containsEntry("wordCount", "12")
                .containsEntry("bip39Checksum", "VALID");
        assertThat(result.evidence().toString()).doesNotContain("abandon");
    }

    @Test
    @DisplayName("PUBLIC_BALANCE: tra số dư thành công trả REAL; khi mạng lỗi báo thất bại, không tự tạo số dư")
    void publicBalance_realAndNoFabricatedFallback() {
        DigitalAsset asset = cryptoAsset("Ethereum", TEST_ETH_ADDRESS, null);
        verifier.setBalanceLookup((net, addr) ->
                new CryptoWalletAssetVerifier.BalanceLookupResult("1.5 ETH", "publicnode"));

        AssetVerifier.VerifyResult realResult = verifier.confirm(
                asset, AssetVerificationMethod.PUBLIC_BALANCE, Map.of(), ownerId, null);
        assertThat(realResult.success()).isTrue();
        assertThat(realResult.provider()).isEqualTo(VerificationProvider.REAL);
        assertThat(realResult.evidence()).containsEntry("balance", "1.5 ETH");

        verifier.setBalanceLookup((net, addr) -> {
            throw new IOException("Network unreachable");
        });
        AssetVerifier.VerifyResult offlineResult = verifier.confirm(
                asset, AssetVerificationMethod.PUBLIC_BALANCE, Map.of(), ownerId, null);
        assertThat(offlineResult.success()).isFalse();
        assertThat(offlineResult.evidence()).isEmpty();
    }

    @Test
    @DisplayName("BIP39_CHECK: không nhận seed phrase gửi trong payload, chỉ kiểm seed đã lưu")
    void bip39_ignoresSeedPhraseInPayload() {
        DigitalAsset asset = cryptoAsset("Ethereum", TEST_ETH_ADDRESS, null);

        AssetVerifier.VerifyResult result = verifier.confirm(asset, AssetVerificationMethod.BIP39_CHECK,
                Map.of("seedPhrase", VALID_BIP39_12, "mnemonic", VALID_BIP39_12), ownerId, null);

        assertThat(result.success()).isFalse();
        verifyNoInteractions(cryptoService);
    }

    @Test
    @DisplayName("WALLET_SIGNATURE: ký thông điệp thử thách bằng khóa bí mật ví → ecRecover khớp địa chỉ khai báo")
    void walletSignature_validPersonalSign_success() {
        DigitalAsset asset = cryptoAsset("Ethereum", TEST_ETH_ADDRESS, null);
        when(otpGenerator.generate()).thenReturn("654321");

        AssetVerifier.StartResult start = verifier.start(asset, AssetVerificationMethod.WALLET_SIGNATURE, ownerId);
        String challengeMessage = start.clientData().get("challengeMessage");
        String signature = CryptoVerificationUtils.signEthereumPersonalMessage(challengeMessage, TEST_PRIV_KEY);

        AssetVerifier.VerifyResult confirm = verifier.confirm(
                asset,
                AssetVerificationMethod.WALLET_SIGNATURE,
                Map.of("signature", signature, "challengeMessage", challengeMessage),
                ownerId,
                start.challengeHash()
        );

        assertThat(confirm.success()).isTrue();
        assertThat(confirm.provider()).isEqualTo(VerificationProvider.REAL);
        assertThat(confirm.evidence()).containsEntry("recoveredAddress", TEST_ETH_ADDRESS);
    }

    @Test
    @DisplayName("WALLET_SIGNATURE: ký bằng ví khác (không phải chủ địa chỉ đã khai) → thất bại")
    void walletSignature_differentWalletKey_fails() {
        DigitalAsset asset = cryptoAsset("Ethereum", TEST_ETH_ADDRESS, null);
        when(otpGenerator.generate()).thenReturn("654321");

        AssetVerifier.StartResult start = verifier.start(asset, AssetVerificationMethod.WALLET_SIGNATURE, ownerId);
        String challengeMessage = start.clientData().get("challengeMessage");
        String otherPrivKey = "0x1111111111111111111111111111111111111111111111111111111111111111";
        String wrongSignature = CryptoVerificationUtils.signEthereumPersonalMessage(challengeMessage, otherPrivKey);

        AssetVerifier.VerifyResult confirm = verifier.confirm(
                asset,
                AssetVerificationMethod.WALLET_SIGNATURE,
                Map.of("signature", wrongSignature, "challengeMessage", challengeMessage),
                ownerId,
                start.challengeHash()
        );

        assertThat(confirm.success()).isFalse();
        assertThat(confirm.provider()).isEqualTo(VerificationProvider.REAL);
    }
}
