package com.ltld.app.legacyvault.service.assetverificationservice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CryptoVerificationUtils – C.3.5 thuật toán mật mã xác thực ví Crypto")
class CryptoVerificationUtilsTest {

    @Test
    @DisplayName("EIP-55: các địa chỉ Ethereum chuẩn EIP-55 hợp lệ")
    void eip55_validAddresses() {
        assertThat(CryptoVerificationUtils.isValidEip55Address("0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed")).isTrue();
        assertThat(CryptoVerificationUtils.isValidEip55Address("0xfB6916095ca1df60bB79Ce92cE3Ea74c37c5d359")).isTrue();
        assertThat(CryptoVerificationUtils.isValidEip55Address("0xdbF03B407c01E7cD3CBea99509d93f8DDDC8C6FB")).isTrue();
        assertThat(CryptoVerificationUtils.isValidEip55Address("0xD1220A0cf47c7B9Be7A2E6BA89F429762e7b9aDb")).isTrue();
    }

    @Test
    @DisplayName("EIP-55: sai hoa/thường hoặc địa chỉ zero bị từ chối")
    void eip55_invalidChecksumOrFormat() {
        // Đổi 'A' -> 'a' trong địa chỉ EIP-55 hợp lệ
        assertThat(CryptoVerificationUtils.isValidEip55Address("0x5aaeb6053F3E94C9b9A09f33669435E7Ef1BeAed")).isFalse();
        // Toàn chữ thường (không có checksum EIP-55)
        assertThat(CryptoVerificationUtils.isValidEip55Address("0x5aaeb6053f3e94c9b9a09f33669435e7ef1beaed")).isFalse();
        // Địa chỉ zero
        assertThat(CryptoVerificationUtils.isValidEip55Address("0x0000000000000000000000000000000000000000")).isFalse();
        // Sai độ dài
        assertThat(CryptoVerificationUtils.isValidEip55Address("0x12345")).isFalse();
    }

    @Test
    @DisplayName("Bitcoin: Base58Check (P2PKH / P2SH) và Bech32 / Bech32m (SegWit / Taproot)")
    void bitcoin_validAndInvalidAddresses() {
        // Satoshi Genesis P2PKH (Base58Check)
        assertThat(CryptoVerificationUtils.isValidBitcoinAddress("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa")).isTrue();
        // P2SH (Base58Check)
        assertThat(CryptoVerificationUtils.isValidBitcoinAddress("3J98t1WpEZ73CNmQviecrnyiWrnqRhWNLy")).isTrue();
        // SegWit v0 Bech32
        assertThat(CryptoVerificationUtils.isValidBitcoinAddress("bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4")).isTrue();
        // Taproot v1 Bech32m
        assertThat(CryptoVerificationUtils.isValidBitcoinAddress(
                "bc1p0xlxvlhemja6c4dqv22uapctqupfhlxm9h8z3k2e72q4k9hcz7vqzk5jj0")).isTrue();

        // Sửa 1 ký tự cuối làm hỏng checksum
        assertThat(CryptoVerificationUtils.isValidBitcoinAddress("1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNb")).isFalse();
        assertThat(CryptoVerificationUtils.isValidBitcoinAddress("bc1qw508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t5")).isFalse();
        // Trộn hoa/thường trong Bech32
        assertThat(CryptoVerificationUtils.isValidBitcoinAddress("bc1qW508d6qejxtdg4y5r3zarvary0c5xw7kv8f3t4")).isFalse();
    }

    @Test
    @DisplayName("BIP-39: kiểm tra wordlist 2048 từ và SHA-256 entropy checksum")
    void bip39_validAndInvalidMnemonics() {
        // Vector chuẩn BIP-39 12 từ (entropy = 0x00..00, từ thứ 12 = about)
        String valid12 = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about";
        CryptoVerificationUtils.Bip39CheckResult res12 = CryptoVerificationUtils.verifyBip39Mnemonic(valid12);
        assertThat(res12.valid()).isTrue();
        assertThat(res12.wordCount()).isEqualTo(12);

        // Vector chuẩn BIP-39 24 từ (entropy = 0x00..00, từ thứ 24 = art)
        String valid24 = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon "
                + "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon art";
        CryptoVerificationUtils.Bip39CheckResult res24 = CryptoVerificationUtils.verifyBip39Mnemonic(valid24);
        assertThat(res24.valid()).isTrue();
        assertThat(res24.wordCount()).isEqualTo(24);

        // Đổi từ cuối 'about' -> 'abandon' (đúng wordlist nhưng sai checksum!)
        String badChecksum = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon";
        assertThat(CryptoVerificationUtils.verifyBip39Mnemonic(badChecksum).valid()).isFalse();

        // Từ không nằm trong wordlist BIP-39
        String unknownWord = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon legacyvault";
        assertThat(CryptoVerificationUtils.verifyBip39Mnemonic(unknownWord).valid()).isFalse();

        // Sai số lượng từ
        assertThat(CryptoVerificationUtils.verifyBip39Mnemonic("abandon about").valid()).isFalse();
    }

    @Test
    @DisplayName("secp256k1 ecRecover: ký thông điệp EIP-191 personal_sign và khôi phục đúng địa chỉ ví")
    void personalSign_signAndRecoverAddress() {
        String privateKeyHex = "0x4c0883a69102937d6231471b5dbb6204fe5129617082792ae468d01a3f362318";
        String expectedAddress = CryptoVerificationUtils.deriveAddressFromPrivateKey(privateKeyHex);
        String message = "LegacyVault Wallet Ownership Verification\nAddress: "
                + expectedAddress.toLowerCase() + "\nNonce: LV-123456";

        String signature = CryptoVerificationUtils.signEthereumPersonalMessage(message, privateKeyHex);
        String recovered = CryptoVerificationUtils.recoverEthereumAddress(message, signature);

        assertThat(recovered).isEqualTo(expectedAddress);

        // Nếu thông điệp bị sửa đổi, địa chỉ khôi phục sẽ khác hẳn địa chỉ gốc
        String recoveredTampered = CryptoVerificationUtils.recoverEthereumAddress(message + "!", signature);
        assertThat(recoveredTampered).isNotEqualTo(expectedAddress);

        // Chữ ký sai định dạng trả về null
        assertThat(CryptoVerificationUtils.recoverEthereumAddress(message, "0x1234")).isNull();
    }
}
