package com.ltld.app.legacyvault.service.assetverificationservice;

import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.ec.CustomNamedCurves;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.crypto.signers.HMacDSAKCalculator;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.bouncycastle.math.ec.ECAlgorithms;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.math.ec.FixedPointCombMultiplier;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

/**
 * C.3.5: Các thuật toán mật mã học phục vụ xác thực ví Crypto và tài sản số:
 * - Keccak-256 & EIP-55 mixed-case checksum cho địa chỉ Ethereum / BNB Chain
 * - Base58Check & Bech32 / Bech32m (BIP-173 / BIP-350) cho địa chỉ Bitcoin
 * - BIP-39 (2048 từ tiếng Anh + kiểm tra entropy checksum SHA-256)
 * - EIP-191 personal_sign & secp256k1 ECDSA public-key recovery (ecRecover)
 */
public final class CryptoVerificationUtils {

    private static final String BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
    private static final String BECH32_CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l";
    private static final int BECH32_CONST = 1;
    private static final int BECH32M_CONST = 0x2bc830a3;

    private static final X9ECParameters SECP256K1_PARAMS = CustomNamedCurves.getByName("secp256k1");
    private static final ECDomainParameters SECP256K1_DOMAIN = new ECDomainParameters(
            SECP256K1_PARAMS.getCurve(),
            SECP256K1_PARAMS.getG(),
            SECP256K1_PARAMS.getN(),
            SECP256K1_PARAMS.getH()
    );
    private static final BigInteger HALF_CURVE_ORDER = SECP256K1_PARAMS.getN().shiftRight(1);

    private static final Map<String, Integer> BIP39_WORD_INDEX = loadBip39Wordlist();

    private CryptoVerificationUtils() {
    }

    // =========================================================
    // SHA-256 & Keccak-256
    // =========================================================

    public static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    public static String sha256Hex(String input) {
        return HexFormat.of().formatHex(sha256(input.getBytes(StandardCharsets.UTF_8)));
    }

    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(
                a.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8),
                b.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)
        );
    }

    public static byte[] keccak256(byte[] input) {
        Keccak.Digest256 digest = new Keccak.Digest256();
        return digest.digest(input);
    }

    // =========================================================
    // Ethereum / EVM: EIP-55 Address Checksum
    // =========================================================

    public static boolean isHexEvmAddressFormat(String address) {
        if (address == null || !address.matches("^0x[0-9a-fA-F]{40}$")) {
            return false;
        }
        // Không chấp nhận địa chỉ zero 0x000...000
        return !address.substring(2).matches("^0{40}$");
    }

    /**
     * Chuyển một địa chỉ 0x... 40 ký tự hex sang định dạng chuẩn EIP-55 mixed-case checksum.
     */
    public static String toEip55Address(String address) {
        if (!isHexEvmAddressFormat(address)) {
            throw new IllegalArgumentException("Invalid EVM hex address format");
        }
        String lowerHex = address.substring(2).toLowerCase(Locale.ROOT);
        String hashHex = HexFormat.of().formatHex(keccak256(lowerHex.getBytes(StandardCharsets.US_ASCII)));
        StringBuilder sb = new StringBuilder(42);
        sb.append("0x");
        for (int i = 0; i < 40; i++) {
            char c = lowerHex.charAt(i);
            if (c >= 'a' && c <= 'f') {
                int nibble = Character.digit(hashHex.charAt(i), 16);
                sb.append(nibble >= 8 ? Character.toUpperCase(c) : c);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Kiểm tra địa chỉ EVM theo chuẩn EIP-55: phải đúng định dạng 0x + 40 ký tự hex và khớp đúng chữ hoa/thường EIP-55.
     */
    public static boolean isValidEip55Address(String address) {
        if (!isHexEvmAddressFormat(address)) {
            return false;
        }
        return address.equals(toEip55Address(address));
    }

    // =========================================================
    // Bitcoin: Base58Check & Bech32 / Bech32m
    // =========================================================

    public static boolean isValidBitcoinAddress(String address) {
        if (address == null || address.isBlank()) {
            return false;
        }
        String trimmed = address.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("bc1") || lower.startsWith("tb1")) {
            return isValidBitcoinBech32(trimmed);
        }
        return isValidBitcoinBase58Check(trimmed);
    }

    public static boolean isValidBitcoinBase58Check(String address) {
        if (address == null || address.length() < 26 || address.length() > 35) {
            return false;
        }
        byte[] decoded = decodeBase58(address);
        if (decoded == null || decoded.length != 25) {
            return false;
        }
        int version = decoded[0] & 0xFF;
        // Mainnet P2PKH (0x00 -> '1'), P2SH (0x05 -> '3'), Testnet P2PKH (0x6F -> 'm'/'n'), Testnet P2SH (0xC4 -> '2')
        if (version != 0x00 && version != 0x05 && version != 0x6F && version != 0xC4) {
            return false;
        }
        byte[] payload = Arrays.copyOfRange(decoded, 0, 21);
        byte[] checksum = Arrays.copyOfRange(decoded, 21, 25);
        byte[] doubleHash = sha256(sha256(payload));
        for (int i = 0; i < 4; i++) {
            if (checksum[i] != doubleHash[i]) {
                return false;
            }
        }
        return true;
    }

    public static boolean isValidBitcoinBech32(String address) {
        if (address == null || address.length() < 14 || address.length() > 90) {
            return false;
        }
        boolean hasLower = false;
        boolean hasUpper = false;
        for (int i = 0; i < address.length(); i++) {
            char c = address.charAt(i);
            if (c < 33 || c > 126) {
                return false;
            }
            if (c >= 'a' && c <= 'z') {
                hasLower = true;
            } else if (c >= 'A' && c <= 'Z') {
                hasUpper = true;
            }
        }
        if (hasLower && hasUpper) {
            return false;
        }
        String lower = address.toLowerCase(Locale.ROOT);
        int pos = lower.lastIndexOf('1');
        if (pos < 1 || pos + 7 > lower.length()) {
            return false;
        }
        String hrp = lower.substring(0, pos);
        if (!"bc".equals(hrp) && !"tb".equals(hrp)) {
            return false;
        }
        int dataLen = lower.length() - pos - 1;
        int[] data = new int[dataLen];
        for (int i = 0; i < dataLen; i++) {
            int idx = BECH32_CHARSET.indexOf(lower.charAt(pos + 1 + i));
            if (idx < 0) {
                return false;
            }
            data[i] = idx;
        }
        int polymod = bech32Polymod(hrp, data);
        int witnessVersion = data[0];
        if (witnessVersion > 16) {
            return false;
        }
        if (witnessVersion == 0 && polymod != BECH32_CONST) {
            return false;
        }
        if (witnessVersion > 0 && polymod != BECH32M_CONST) {
            return false;
        }
        byte[] program = convertBits5To8(Arrays.copyOfRange(data, 1, data.length - 6));
        if (program == null || program.length < 2 || program.length > 40) {
            return false;
        }
        if (witnessVersion == 0 && program.length != 20 && program.length != 32) {
            return false;
        }
        return true;
    }

    private static int bech32Polymod(String hrp, int[] values) {
        int chk = 1;
        for (int i = 0; i < hrp.length(); i++) {
            chk = polymodStep(chk, hrp.charAt(i) >> 5);
        }
        chk = polymodStep(chk, 0);
        for (int i = 0; i < hrp.length(); i++) {
            chk = polymodStep(chk, hrp.charAt(i) & 0x1f);
        }
        for (int v : values) {
            chk = polymodStep(chk, v);
        }
        return chk;
    }

    private static int polymodStep(int pre, int val) {
        int b = pre >> 25;
        int chk = ((pre & 0x1ffffff) << 5) ^ val;
        if ((b & 1) != 0) chk ^= 0x3b6a57b2;
        if ((b & 2) != 0) chk ^= 0x26508e6d;
        if ((b & 4) != 0) chk ^= 0x1ea119fa;
        if ((b & 8) != 0) chk ^= 0x3d4233dd;
        if ((b & 16) != 0) chk ^= 0x2a1462b3;
        return chk;
    }

    private static byte[] convertBits5To8(int[] data5) {
        int acc = 0;
        int bits = 0;
        byte[] temp = new byte[(data5.length * 5) / 8];
        int outIdx = 0;
        for (int value : data5) {
            if (value < 0 || (value >> 5) != 0) {
                return null;
            }
            acc = (acc << 5) | value;
            bits += 5;
            while (bits >= 8) {
                bits -= 8;
                temp[outIdx++] = (byte) ((acc >> bits) & 0xff);
            }
        }
        if (bits >= 5 || ((acc << (8 - bits)) & 0xff) != 0) {
            return null;
        }
        return Arrays.copyOf(temp, outIdx);
    }

    public static boolean isValidSolanaAddress(String address) {
        if (address == null || address.length() < 32 || address.length() > 44) {
            return false;
        }
        byte[] decoded = decodeBase58(address.trim());
        return decoded != null && decoded.length == 32;
    }

    public static byte[] decodeBase58(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        BigInteger num = BigInteger.ZERO;
        BigInteger base = BigInteger.valueOf(58);
        for (int i = 0; i < input.length(); i++) {
            int digit = BASE58_ALPHABET.indexOf(input.charAt(i));
            if (digit < 0) {
                return null;
            }
            num = num.multiply(base).add(BigInteger.valueOf(digit));
        }
        byte[] numBytes = num.toByteArray();
        int stripSignByte = (numBytes.length > 1 && numBytes[0] == 0) ? 1 : 0;
        int leadingZeros = 0;
        while (leadingZeros < input.length() && input.charAt(leadingZeros) == '1') {
            leadingZeros++;
        }
        byte[] decoded = new byte[leadingZeros + numBytes.length - stripSignByte];
        System.arraycopy(numBytes, stripSignByte, decoded, leadingZeros, numBytes.length - stripSignByte);
        return decoded;
    }

    // =========================================================
    // BIP-39 Wordlist & Checksum Verification
    // =========================================================

    public record Bip39CheckResult(boolean valid, int wordCount, String errorMessage) {
    }

    public static Bip39CheckResult verifyBip39Mnemonic(String phrase) {
        if (phrase == null || phrase.isBlank()) {
            return new Bip39CheckResult(false, 0, "Seed phrase is empty");
        }
        String[] words = phrase.trim().toLowerCase(Locale.ROOT).split("\\s+");
        int n = words.length;
        if (n != 12 && n != 24) {
            return new Bip39CheckResult(false, n, "Seed phrase must contain 12 or 24 words (found " + n + ")");
        }
        int totalBits = n * 11;
        int checksumBits = n / 3;
        int entropyBits = totalBits - checksumBits;
        byte[] entropy = new byte[entropyBits / 8];

        int bitIndex = 0;
        int declaredChecksum = 0;
        for (String w : words) {
            Integer idx = BIP39_WORD_INDEX.get(w);
            if (idx == null) {
                return new Bip39CheckResult(false, n, "Word not in BIP-39 English wordlist: '" + w + "'");
            }
            for (int b = 10; b >= 0; b--) {
                int bit = (idx >> b) & 1;
                if (bitIndex < entropyBits) {
                    int bytePos = bitIndex / 8;
                    int bitOffset = 7 - (bitIndex % 8);
                    entropy[bytePos] |= (byte) (bit << bitOffset);
                } else {
                    declaredChecksum = (declaredChecksum << 1) | bit;
                }
                bitIndex++;
            }
        }

        byte[] hash = sha256(entropy);
        int expectedChecksum = (hash[0] & 0xFF) >> (8 - checksumBits);
        if (declaredChecksum != expectedChecksum) {
            return new Bip39CheckResult(false, n, "BIP-39 mnemonic checksum is invalid");
        }
        return new Bip39CheckResult(true, n, "Valid BIP-39 mnemonic (" + n + " words)");
    }

    private static Map<String, Integer> loadBip39Wordlist() {
        Map<String, Integer> map = new HashMap<>(2048);
        try (InputStream is = CryptoVerificationUtils.class.getResourceAsStream("/bip39-english.txt")) {
            if (is == null) {
                throw new IllegalStateException("Missing /bip39-english.txt resource");
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                int index = 0;
                while ((line = reader.readLine()) != null) {
                    String word = line.trim();
                    if (!word.isEmpty()) {
                        map.put(word, index++);
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load BIP-39 wordlist", e);
        }
        return Collections.unmodifiableMap(map);
    }

    // =========================================================
    // Ethereum EIP-191 personal_sign & secp256k1 ecRecover
    // =========================================================

    public static byte[] computeEip191MessageHash(String message) {
        byte[] msgBytes = message.getBytes(StandardCharsets.UTF_8);
        byte[] prefix = ("\u0019Ethereum Signed Message:\n" + msgBytes.length).getBytes(StandardCharsets.UTF_8);
        byte[] combined = new byte[prefix.length + msgBytes.length];
        System.arraycopy(prefix, 0, combined, 0, prefix.length);
        System.arraycopy(msgBytes, 0, combined, prefix.length, msgBytes.length);
        return keccak256(combined);
    }

    /**
     * Khôi phục địa chỉ ví Ethereum (EIP-55) từ thông điệp gốc và chữ ký 65-byte hex (MetaMask personal_sign).
     * Trả về null nếu chữ ký sai định dạng hoặc không khôi phục được khóa công khai hợp lệ.
     */
    public static String recoverEthereumAddress(String message, String signatureHex) {
        if (message == null || signatureHex == null) {
            return null;
        }
        String cleanHex = signatureHex.trim();
        if (cleanHex.startsWith("0x") || cleanHex.startsWith("0X")) {
            cleanHex = cleanHex.substring(2);
        }
        if (!cleanHex.matches("^[0-9a-fA-F]{130}$")) {
            return null;
        }
        byte[] sigBytes;
        try {
            sigBytes = HexFormat.of().parseHex(cleanHex);
        } catch (IllegalArgumentException e) {
            return null;
        }

        BigInteger r = new BigInteger(1, Arrays.copyOfRange(sigBytes, 0, 32));
        BigInteger s = new BigInteger(1, Arrays.copyOfRange(sigBytes, 32, 64));
        int v = sigBytes[64] & 0xFF;
        if (v < 27) {
            v += 27;
        } else if (v >= 35) {
            v = 27 + ((v - 35) % 2);
        }
        if (v != 27 && v != 28) {
            return null;
        }
        int recId = v - 27;

        BigInteger n = SECP256K1_DOMAIN.getN();
        if (r.signum() <= 0 || r.compareTo(n) >= 0 || s.signum() <= 0 || s.compareTo(n) >= 0) {
            return null;
        }

        byte[] messageHash = computeEip191MessageHash(message);
        try {
            ECPoint q = recoverPublicKeyPoint(recId, r, s, messageHash);
            if (q == null || q.isInfinity()) {
                return null;
            }
            byte[] encoded = q.getEncoded(false); // 65 bytes: 0x04 || X (32) || Y (32)
            byte[] pubKeyHash = keccak256(Arrays.copyOfRange(encoded, 1, encoded.length));
            byte[] addressBytes = Arrays.copyOfRange(pubKeyHash, 12, 32);
            return toEip55Address("0x" + HexFormat.of().formatHex(addressBytes));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static ECPoint recoverPublicKeyPoint(int recId, BigInteger r, BigInteger s, byte[] messageHash) {
        BigInteger n = SECP256K1_DOMAIN.getN();
        BigInteger i = BigInteger.valueOf((long) recId / 2);
        BigInteger x = r.add(i.multiply(n));
        BigInteger prime = SECP256K1_DOMAIN.getCurve().getField().getCharacteristic();
        if (x.compareTo(prime) >= 0) {
            return null;
        }
        byte[] compEnc = new byte[33];
        compEnc[0] = (byte) ((recId & 1) == 1 ? 0x03 : 0x02);
        byte[] xBytes = toFixedLengthBytes(x, 32);
        System.arraycopy(xBytes, 0, compEnc, 1, 32);

        ECPoint R = SECP256K1_DOMAIN.getCurve().decodePoint(compEnc);
        if (!R.multiply(n).isInfinity()) {
            return null;
        }
        BigInteger e = new BigInteger(1, messageHash);
        BigInteger eInv = BigInteger.ZERO.subtract(e).mod(n);
        BigInteger rInv = r.modInverse(n);
        BigInteger srInv = rInv.multiply(s).mod(n);
        BigInteger eInvrInv = rInv.multiply(eInv).mod(n);
        return ECAlgorithms.sumOfTwoMultiplies(SECP256K1_DOMAIN.getG(), eInvrInv, R, srInv).normalize();
    }

    /**
     * Ký một thông điệp theo chuẩn EIP-191 personal_sign bằng khóa bí mật secp256k1 (32 byte hex)
     * và trả về chữ ký 0x... (130 ký tự hex) cùng địa chỉ ví tương ứng.
     */
    public static String signEthereumPersonalMessage(String message, String privateKeyHex) {
        BigInteger privKey = parsePrivateKey(privateKeyHex);
        byte[] messageHash = computeEip191MessageHash(message);

        ECDSASigner signer = new ECDSASigner(new HMacDSAKCalculator(new SHA256Digest()));
        signer.init(true, new ECPrivateKeyParameters(privKey, SECP256K1_DOMAIN));
        BigInteger[] components = signer.generateSignature(messageHash);
        BigInteger r = components[0];
        BigInteger s = components[1];
        if (s.compareTo(HALF_CURVE_ORDER) > 0) {
            s = SECP256K1_DOMAIN.getN().subtract(s);
        }

        String expectedAddress = deriveAddressFromPrivateKey(privateKeyHex);
        int recId = -1;
        for (int candidate = 0; candidate < 2; candidate++) {
            ECPoint q = recoverPublicKeyPoint(candidate, r, s, messageHash);
            if (q != null && !q.isInfinity()) {
                byte[] encoded = q.getEncoded(false);
                byte[] pubKeyHash = keccak256(Arrays.copyOfRange(encoded, 1, encoded.length));
                String addr = toEip55Address("0x" + HexFormat.of().formatHex(Arrays.copyOfRange(pubKeyHash, 12, 32)));
                if (addr.equalsIgnoreCase(expectedAddress)) {
                    recId = candidate;
                    break;
                }
            }
        }
        if (recId < 0) {
            throw new IllegalStateException("Could not determine recovery ID for signature");
        }

        byte[] sig = new byte[65];
        System.arraycopy(toFixedLengthBytes(r, 32), 0, sig, 0, 32);
        System.arraycopy(toFixedLengthBytes(s, 32), 0, sig, 32, 32);
        sig[64] = (byte) (27 + recId);
        return "0x" + HexFormat.of().formatHex(sig);
    }

    public static String deriveAddressFromPrivateKey(String privateKeyHex) {
        BigInteger privKey = parsePrivateKey(privateKeyHex);
        ECPoint pubPoint = new FixedPointCombMultiplier().multiply(SECP256K1_DOMAIN.getG(), privKey).normalize();
        byte[] encoded = pubPoint.getEncoded(false);
        byte[] pubKeyHash = keccak256(Arrays.copyOfRange(encoded, 1, encoded.length));
        return toEip55Address("0x" + HexFormat.of().formatHex(Arrays.copyOfRange(pubKeyHash, 12, 32)));
    }

    private static BigInteger parsePrivateKey(String privateKeyHex) {
        String clean = privateKeyHex.trim();
        if (clean.startsWith("0x") || clean.startsWith("0X")) {
            clean = clean.substring(2);
        }
        return new BigInteger(clean, 16);
    }

    private static byte[] toFixedLengthBytes(BigInteger value, int length) {
        byte[] raw = value.toByteArray();
        if (raw.length == length) {
            return raw;
        }
        byte[] result = new byte[length];
        if (raw.length > length) {
            System.arraycopy(raw, raw.length - length, result, 0, length);
        } else {
            System.arraycopy(raw, 0, result, length - raw.length, raw.length);
        }
        return result;
    }
}
