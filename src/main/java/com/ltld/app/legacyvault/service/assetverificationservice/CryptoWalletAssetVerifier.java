package com.ltld.app.legacyvault.service.assetverificationservice;

import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.AssetVerificationMethod;
import com.ltld.app.legacyvault.enums.VerificationProvider;
import com.ltld.app.legacyvault.exception.VaultException;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import com.ltld.app.legacyvault.service.vaultservice.assetschema.FlatJson;
import com.ltld.app.legacyvault.utility.OtpGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * C.3.5: Xác thực tài sản CRYPTO_WALLET:
 * - ADDRESS_CHECKSUM (REAL): kiểm tra checksum địa chỉ (EIP-55 Keccak-256 cho Ethereum/BNB, Base58Check & Bech32/Bech32m cho Bitcoin, Base58 32-byte cho Solana).
 * - BIP39_CHECK (REAL): kiểm seed phrase ĐÃ LƯU (mã hóa) trong tài sản theo wordlist BIP-39 2048 từ + SHA-256 checksum.
 *   Không nhận seed phrase gửi lên từ request.
 * - PUBLIC_BALANCE (REAL): tra cứu số dư công khai trên blockchain; không tra được thì báo thất bại, không tự tạo số.
 * Lưu ý: ADDRESS_CHECKSUM, BIP39_CHECK, PUBLIC_BALANCE chỉ chứng minh dữ liệu HỢP LỆ; chỉ WALLET_SIGNATURE chứng minh SỞ HỮU.
 * - WALLET_SIGNATURE (REAL): tạo thông điệp thử thách và khôi phục địa chỉ từ chữ ký MetaMask personal_sign (EIP-191 + secp256k1 ecRecover).
 */
@Slf4j
@Component
public class CryptoWalletAssetVerifier implements AssetVerifier {

    private static final int CHALLENGE_TTL_MINUTES = 10;
    private static final Duration CONNECT_TIMEOUT = Duration.ofMillis(2000);
    private static final Duration READ_TIMEOUT = Duration.ofMillis(3000);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    private static final Pattern JSON_RESULT_HEX = Pattern.compile("\"result\"\\s*:\\s*\"(0x[0-9a-fA-F]+)\"");
    private static final Pattern FUNDED_SUM_PATTERN = Pattern.compile("\"funded_txo_sum\"\\s*:\\s*(\\d+)");
    private static final Pattern SPENT_SUM_PATTERN = Pattern.compile("\"spent_txo_sum\"\\s*:\\s*(\\d+)");
    private static final Pattern SOLANA_VALUE_PATTERN = Pattern.compile("\"value\"\\s*:\\s*(\\d+)");

    private final CryptoService cryptoService;
    private final OtpGenerator otpGenerator;
    private PublicBalanceLookup balanceLookup;


    @FunctionalInterface
    public interface PublicBalanceLookup {
        BalanceLookupResult lookup(String network, String walletAddress) throws IOException, InterruptedException;
    }

    public record BalanceLookupResult(String balanceFormatted, String source) {
    }

    @Autowired
    public CryptoWalletAssetVerifier(CryptoService cryptoService, OtpGenerator otpGenerator) {
        this.cryptoService = cryptoService;
        this.otpGenerator = otpGenerator;
        this.balanceLookup = this::fetchPublicBalanceOverHttp;
    }

    // Cho phép unit test tiêm mock lookup mà không cần gọi mạng thật
    void setBalanceLookup(PublicBalanceLookup balanceLookup) {
        this.balanceLookup = balanceLookup;
    }

    @Override
    public AssetType type() {
        return AssetType.CRYPTO_WALLET;
    }

    @Override
    public List<AssetVerificationMethod> methods() {
        return List.of(
                AssetVerificationMethod.ADDRESS_CHECKSUM,
                AssetVerificationMethod.BIP39_CHECK,
                AssetVerificationMethod.PUBLIC_BALANCE,
                AssetVerificationMethod.WALLET_SIGNATURE
        );
    }

    @Override
    public StartResult start(DigitalAsset asset, AssetVerificationMethod method, UUID ownerId) {
        Map<String, String> fields = FlatJson.decode(asset.getPublicFieldsJson());
        String network = fields.getOrDefault("blockchainNetwork", "Ethereum").trim();
        String address = requireWalletAddress(fields.get("walletAddress"));

        return switch (method) {
            case ADDRESS_CHECKSUM -> startAddressChecksum(network, address);
            case BIP39_CHECK -> new StartResult(
                    VerificationProvider.REAL,
                    "Ready to verify BIP-39 mnemonic wordlist and entropy checksum",
                    null,
                    Map.of("network", network, "walletAddress", address),
                    LocalDateTime.now().plusMinutes(CHALLENGE_TTL_MINUTES)
            );
            case PUBLIC_BALANCE -> new StartResult(
                    VerificationProvider.REAL,
                    "Ready to query public blockchain balance for " + address,
                    null,
                    Map.of("network", network, "walletAddress", address),
                    LocalDateTime.now().plusMinutes(CHALLENGE_TTL_MINUTES)
            );
            case WALLET_SIGNATURE -> startWalletSignature(asset, network, address);
            default -> throw new VaultException("Unsupported verification method for CRYPTO_WALLET: " + method,
                    HttpStatus.BAD_REQUEST);
        };
    }

    @Override
    public VerifyResult confirm(DigitalAsset asset, AssetVerificationMethod method,
                                Map<String, String> payload, UUID ownerId) {
        // Mã thử thách lưu ở DB; service truyền vào qua hàm 5 tham số. Gọi hàm này thì các cách cần mã sẽ trả thất bại.
        return confirm(asset, method, payload, ownerId, null);
    }

    // Ghi đè hàm default 5 tham số của AssetVerifier (thêm ở #199). Không đặt @Override để vẫn biên dịch được trước khi #199 merge.
    public VerifyResult confirm(DigitalAsset asset, AssetVerificationMethod method,
                                Map<String, String> payload, UUID ownerId, String challengeHash) {
        Map<String, String> fields = FlatJson.decode(asset.getPublicFieldsJson());
        String network = fields.getOrDefault("blockchainNetwork", "Ethereum").trim();
        String address = requireWalletAddress(fields.get("walletAddress"));
        Map<String, String> safePayload = payload == null ? Map.of() : payload;

        return switch (method) {
            case ADDRESS_CHECKSUM -> confirmAddressChecksum(network, address);
            case BIP39_CHECK -> confirmBip39Check(asset);
            case PUBLIC_BALANCE -> confirmPublicBalance(network, address);
            case WALLET_SIGNATURE -> confirmWalletSignature(asset, network, address, safePayload, challengeHash);
            default -> throw new VaultException("Unsupported verification method for CRYPTO_WALLET: " + method,
                    HttpStatus.BAD_REQUEST);
        };
    }

    // =========================================================
    // 1. ADDRESS_CHECKSUM
    // =========================================================

    private StartResult startAddressChecksum(String network, String address) {
        Map<String, String> clientData = new LinkedHashMap<>();
        clientData.put("network", network);
        clientData.put("walletAddress", address);
        if (CryptoVerificationUtils.isHexEvmAddressFormat(address)) {
            clientData.put("expectedEip55", CryptoVerificationUtils.toEip55Address(address));
        }
        return new StartResult(
                VerificationProvider.REAL,
                "Ready to verify cryptographic address checksum for " + network,
                null,
                clientData,
                LocalDateTime.now().plusMinutes(CHALLENGE_TTL_MINUTES)
        );
    }

    private VerifyResult confirmAddressChecksum(String network, String address) {
        String normNet = network.toLowerCase(Locale.ROOT);
        boolean valid;
        String scheme;

        if (normNet.contains("ethereum") || normNet.contains("bnb")) {
            valid = CryptoVerificationUtils.isValidEip55Address(address);
            scheme = "EIP-55 (Keccak-256)";
        } else if (normNet.contains("bitcoin")) {
            valid = CryptoVerificationUtils.isValidBitcoinAddress(address);
            scheme = address.toLowerCase(Locale.ROOT).startsWith("bc1")
                    || address.toLowerCase(Locale.ROOT).startsWith("tb1")
                    ? "Bech32/Bech32m (BIP-173/BIP-350)"
                    : "Base58Check (Double SHA-256)";
        } else if (normNet.contains("solana")) {
            valid = CryptoVerificationUtils.isValidSolanaAddress(address);
            scheme = "Base58 (Ed25519 32-byte)";
        } else {
            if (address.startsWith("0x") || address.startsWith("0X")) {
                valid = CryptoVerificationUtils.isValidEip55Address(address);
                scheme = "EIP-55 (Keccak-256)";
            } else if (address.startsWith("1") || address.startsWith("3")
                    || address.toLowerCase(Locale.ROOT).startsWith("bc1")
                    || address.toLowerCase(Locale.ROOT).startsWith("tb1")) {
                valid = CryptoVerificationUtils.isValidBitcoinAddress(address);
                scheme = "Bitcoin Checksum";
            } else {
                valid = CryptoVerificationUtils.isValidSolanaAddress(address);
                scheme = "Base58";
            }
        }

        if (!valid) {
            return new VerifyResult(false, VerificationProvider.REAL,
                    "Wallet address failed " + scheme + " checksum validation for network " + network,
                    Map.of());
        }

        Map<String, String> evidence = new LinkedHashMap<>();
        evidence.put("network", network);
        evidence.put("walletAddress", address);
        evidence.put("checksumScheme", scheme);
        return new VerifyResult(true, VerificationProvider.REAL,
                "Wallet address checksum verified (" + scheme + ")", evidence);
    }

    // =========================================================
    // 2. BIP39_CHECK
    // =========================================================

    private VerifyResult confirmBip39Check(DigitalAsset asset) {
        // Quy ước C.2: KHÔNG nhận seed phrase gửi lên từ request. Chỉ kiểm seed phrase Owner đã lưu (mã hóa) trong tài sản.
        String phrase = null;
        if (asset.getEncryptedSecret() != null && !asset.getEncryptedSecret().isBlank()) {
            try {
                String decrypted = cryptoService.decrypt(asset.getEncryptedSecret());
                Map<String, String> secretFields = FlatJson.decode(decrypted);
                phrase = secretFields.get("seedPhrase");
            } catch (Exception e) {
                log.warn("Could not decrypt asset secret for BIP-39 verification on asset {}: {}",
                        asset.getId(), e.getMessage());
            }
        }

        if (phrase == null || phrase.isBlank()) {
            return new VerifyResult(false, VerificationProvider.REAL,
                    "No seed phrase is stored for this asset. Save it in the asset's secret fields first", Map.of());
        }
        CryptoVerificationUtils.Bip39CheckResult check = CryptoVerificationUtils.verifyBip39Mnemonic(phrase);
        if (!check.valid()) {
            return new VerifyResult(false, VerificationProvider.REAL, check.errorMessage(), Map.of());
        }

        // Quy tắc C.2: tuyệt đối KHÔNG lưu từ khóa seed phrase vào evidence
        Map<String, String> evidence = new LinkedHashMap<>();
        evidence.put("wordCount", String.valueOf(check.wordCount()));
        evidence.put("bip39Checksum", "VALID");
        return new VerifyResult(true, VerificationProvider.REAL, check.errorMessage(), evidence);
    }

    // =========================================================
    // 3. PUBLIC_BALANCE
    // =========================================================

    private VerifyResult confirmPublicBalance(String network, String address) {
        if (!isStructurallyValidAddress(network, address)) {
            return new VerifyResult(false, VerificationProvider.REAL,
                    "Invalid wallet address format for network " + network, Map.of());
        }

        try {
            BalanceLookupResult lookupResult = balanceLookup.lookup(network, address);
            if (lookupResult != null && lookupResult.balanceFormatted() != null) {
                Map<String, String> evidence = new LinkedHashMap<>();
                evidence.put("network", network);
                evidence.put("walletAddress", address);
                evidence.put("balance", lookupResult.balanceFormatted());
                evidence.put("source", lookupResult.source() != null ? lookupResult.source() : "public-rpc");
                return new VerifyResult(true, VerificationProvider.REAL,
                        "Public blockchain balance verified: " + lookupResult.balanceFormatted(), evidence);
            }
        } catch (Exception e) {
            log.info("Public blockchain lookup unreachable for {} ({}): {}",
                    network, address, e.getMessage());
        }

        // Không tra được thì báo thất bại, KHÔNG tự tạo số dư (tránh dữ liệu bịa)
        return new VerifyResult(false, VerificationProvider.REAL,
                "Could not look up the public balance right now, please try again later", Map.of());
    }

    private BalanceLookupResult fetchPublicBalanceOverHttp(String network, String address)
            throws IOException, InterruptedException {
        HttpClient client = HTTP_CLIENT;
        String normNet = network.toLowerCase(Locale.ROOT);

        if (normNet.contains("bitcoin")) {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://blockstream.info/api/address/" + address))
                    .timeout(READ_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body() != null) {
                Matcher funded = FUNDED_SUM_PATTERN.matcher(resp.body());
                Matcher spent = SPENT_SUM_PATTERN.matcher(resp.body());
                if (funded.find() && spent.find()) {
                    long satoshis = Math.max(0L,
                            Long.parseLong(funded.group(1)) - Long.parseLong(spent.group(1)));
                    BigDecimal btc = BigDecimal.valueOf(satoshis)
                            .divide(BigDecimal.valueOf(100_000_000L), 8, RoundingMode.HALF_UP);
                    return new BalanceLookupResult(btc.stripTrailingZeros().toPlainString() + " BTC",
                            "blockstream.info");
                }
            }
            return null;
        }

        if (normNet.contains("ethereum") || normNet.contains("bnb")) {
            String rpcUrl = normNet.contains("bnb")
                    ? "https://bsc-dataseed.binance.org"
                    : "https://ethereum-rpc.publicnode.com";
            String unit = normNet.contains("bnb") ? "BNB" : "ETH";
            String body = "{\"jsonrpc\":\"2.0\",\"method\":\"eth_getBalance\",\"params\":[\""
                    + address + "\",\"latest\"],\"id\":1}";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(rpcUrl))
                    .timeout(READ_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body() != null) {
                Matcher m = JSON_RESULT_HEX.matcher(resp.body());
                if (m.find()) {
                    String hex = m.group(1).substring(2);
                    BigInteger wei = hex.isEmpty() ? BigInteger.ZERO : new BigInteger(hex, 16);
                    BigDecimal eth = new BigDecimal(wei)
                            .divide(BigDecimal.TEN.pow(18), 6, RoundingMode.HALF_UP);
                    return new BalanceLookupResult(eth.stripTrailingZeros().toPlainString() + " " + unit, rpcUrl);
                }
            }
            return null;
        }

        if (normNet.contains("solana")) {
            String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"getBalance\",\"params\":[\"" + address + "\"]}";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.mainnet-beta.solana.com"))
                    .timeout(READ_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body() != null) {
                Matcher m = SOLANA_VALUE_PATTERN.matcher(resp.body());
                if (m.find()) {
                    long lamports = Long.parseLong(m.group(1));
                    BigDecimal sol = BigDecimal.valueOf(lamports)
                            .divide(BigDecimal.valueOf(1_000_000_000L), 6, RoundingMode.HALF_UP);
                    return new BalanceLookupResult(sol.stripTrailingZeros().toPlainString() + " SOL",
                            "api.mainnet-beta.solana.com");
                }
            }
        }
        return null;
    }

    private static boolean isStructurallyValidAddress(String network, String address) {
        String normNet = network.toLowerCase(Locale.ROOT);
        if (normNet.contains("ethereum") || normNet.contains("bnb")) {
            return CryptoVerificationUtils.isHexEvmAddressFormat(address);
        }
        if (normNet.contains("bitcoin")) {
            return CryptoVerificationUtils.isValidBitcoinAddress(address);
        }
        if (normNet.contains("solana")) {
            return CryptoVerificationUtils.isValidSolanaAddress(address);
        }
        return CryptoVerificationUtils.isHexEvmAddressFormat(address)
                || CryptoVerificationUtils.isValidBitcoinAddress(address)
                || CryptoVerificationUtils.isValidSolanaAddress(address);
    }

    // =========================================================
    // 4. WALLET_SIGNATURE (EIP-191 personal_sign + secp256k1 ecRecover)
    // =========================================================

    private StartResult startWalletSignature(DigitalAsset asset, String network, String address) {
        if (!CryptoVerificationUtils.isHexEvmAddressFormat(address)) {
            throw new VaultException(
                    "Cryptographic personal_sign verification requires a valid EVM 0x wallet address",
                    HttpStatus.BAD_REQUEST);
        }
        String nonce = "LV-" + otpGenerator.generate();
        String challengeMessage = buildChallengeMessage(address, nonce);
        String challengeHash = CryptoVerificationUtils.sha256Hex(challengeMessage);


        Map<String, String> clientData = new LinkedHashMap<>();
        clientData.put("challengeMessage", challengeMessage);
        clientData.put("nonce", nonce);
        clientData.put("walletAddress", address);
        clientData.put("network", network);

        return new StartResult(
                VerificationProvider.REAL,
                "Sign the challenge message with your wallet using personal_sign",
                challengeHash,
                clientData,
                LocalDateTime.now().plusMinutes(CHALLENGE_TTL_MINUTES)
        );
    }

    private VerifyResult confirmWalletSignature(DigitalAsset asset, String network, String address,
                                                Map<String, String> payload, String challengeHash) {
        if (!CryptoVerificationUtils.isHexEvmAddressFormat(address)) {
            return new VerifyResult(false, VerificationProvider.REAL,
                    "Wallet address is not a valid EVM 0x address", Map.of());
        }
        String signature = firstNonBlank(payload, "signature", "sig");
        if (signature == null) {
            return new VerifyResult(false, VerificationProvider.REAL,
                    "Wallet signature is required", Map.of());
        }
        if (challengeHash == null || challengeHash.isBlank()) {
            return new VerifyResult(false, VerificationProvider.REAL,
                    "Wallet signature challenge has not been started", Map.of());
        }

        String message = resolveSignedMessage(asset, address, payload);
        if (message == null) {
            return new VerifyResult(false, VerificationProvider.REAL,
                    "Challenge message or nonce is missing", Map.of());
        }

        String nonce = firstNonBlank(payload, "nonce");
        boolean challengeMatched = CryptoVerificationUtils.constantTimeEquals(
                CryptoVerificationUtils.sha256Hex(message), challengeHash);

        if (!challengeMatched) {
            return new VerifyResult(false, VerificationProvider.REAL,
                    "Signed challenge message does not match the active challenge", Map.of());
        }

        String recoveredAddress = CryptoVerificationUtils.recoverEthereumAddress(message, signature);
        if (recoveredAddress == null || !recoveredAddress.equalsIgnoreCase(address)) {
            return new VerifyResult(false, VerificationProvider.REAL,
                    "Signature verification failed: recovered address does not match declared wallet address",
                    Map.of());
        }

        Map<String, String> evidence = new LinkedHashMap<>();
        evidence.put("network", network);
        evidence.put("walletAddress", recoveredAddress);
        evidence.put("recoveredAddress", recoveredAddress);
        evidence.put("scheme", "EIP-191 personal_sign (secp256k1)");
        return new VerifyResult(true, VerificationProvider.REAL,
                "Wallet ownership verified via cryptographic signature (secp256k1 ecRecover)", evidence);
    }

    public static String buildChallengeMessage(String address, String nonce) {
        return "LegacyVault Wallet Ownership Verification\nAddress: "
                + address.trim().toLowerCase(Locale.ROOT)
                + "\nNonce: " + nonce.trim();
    }

    private String resolveSignedMessage(DigitalAsset asset, String address, Map<String, String> payload) {
        String explicitMessage = firstNonBlank(payload, "challengeMessage", "message");
        if (explicitMessage != null) {
            return explicitMessage;
        }
        String nonce = firstNonBlank(payload, "nonce");
        if (nonce != null) {
            return buildChallengeMessage(address, nonce);
        }
        return null;
    }

    private static String requireWalletAddress(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new VaultException("Wallet address (walletAddress) is missing", HttpStatus.BAD_REQUEST);
        }
        return raw.trim();
    }

    private static String firstNonBlank(Map<String, String> map, String... keys) {
        for (String key : keys) {
            String v = map.get(key);
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }
}
