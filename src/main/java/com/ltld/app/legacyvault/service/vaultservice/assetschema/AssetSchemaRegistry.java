package com.ltld.app.legacyvault.service.vaultservice.assetschema;

import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.TransferMechanism;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.ltld.app.legacyvault.service.vaultservice.assetschema.AssetFieldSpec.number;
import static com.ltld.app.legacyvault.service.vaultservice.assetschema.AssetFieldSpec.secret;
import static com.ltld.app.legacyvault.service.vaultservice.assetschema.AssetFieldSpec.select;
import static com.ltld.app.legacyvault.service.vaultservice.assetschema.AssetFieldSpec.text;
import static com.ltld.app.legacyvault.service.vaultservice.assetschema.AssetFieldSpec.textarea;

/**
 * N3: khai báo trường cho từng loại tài sản và cơ chế chuyển giao của loại đó.
 * - LEGAL_CLAIM_DOSSIER: ngân hàng / sàn / tài liệu, người nhận làm thủ tục pháp lý.
 * - CRYPTOGRAPHIC_SPLIT_KEY: ví crypto, bí mật chỉ giao cho đúng 1 người nhận.
 * - EXECUTOR_DISPOSITION: MXH / email / ví điện tử, Executor xử lý theo di nguyện (wish).
 */
public final class AssetSchemaRegistry {

    public static final List<String> WISHES = List.of("MEMORIALIZE", "DELETE", "TRANSFER_DATA");
    // 12 hoặc 24 từ cách nhau bằng khoảng trắng (kiểm wordlist BIP-39 để phase sau)
    static final String SEED_PHRASE_PATTERN = "^\\s*(\\S+\\s+){11}\\S+\\s*$|^\\s*(\\S+\\s+){23}\\S+\\s*$";
    private static final List<String> CURRENCIES = List.of("VND", "USD", "EUR");

    private static final Map<AssetType, AssetSchema> SCHEMAS = new EnumMap<>(AssetType.class);

    static {
        register(AssetType.BANK_ACCOUNT, "Bank account", TransferMechanism.LEGAL_CLAIM_DOSSIER, List.of(
                text("institutionName", "Bank name", true),
                text("accountNumber", "Account number", true, "^\\d{6,19}$"),
                text("branchName", "Branch", true),
                number("estimatedValue", "Estimated value", true),
                select("currency", "Currency", true, CURRENCIES),
                textarea("legalInstruction", "Instructions for settling the account", true),
                secret("pinOrPasswordNote", "PIN / password note", false)));

        register(AssetType.EXCHANGE_ACCOUNT, "Exchange account", TransferMechanism.LEGAL_CLAIM_DOSSIER, List.of(
                text("institutionName", "Exchange name", true),
                text("accountId", "Account ID", true),
                number("estimatedValue", "Estimated value", true),
                select("currency", "Currency", true, CURRENCIES),
                textarea("legalInstruction", "Instructions for claiming the account", true),
                secret("pinOrPasswordNote", "PIN / password note", false)));

        register(AssetType.DOCUMENT, "Document", TransferMechanism.LEGAL_CLAIM_DOSSIER, List.of(
                text("documentTitle", "Document title", true),
                text("issuer", "Issuer", false),
                text("location", "Where the original is kept", true),
                textarea("legalInstruction", "Instructions", false)));

        register(AssetType.CRYPTO_WALLET, "Crypto wallet", TransferMechanism.CRYPTOGRAPHIC_SPLIT_KEY, List.of(
                text("walletProvider", "Wallet (MetaMask, Ledger...)", true),
                select("blockchainNetwork", "Network", true,
                        List.of("Bitcoin", "Ethereum", "Solana", "BNB Chain", "Other")),
                text("walletAddress", "Wallet address", true, "^[A-Za-z0-9]{20,100}$"),
                secret("seedPhrase", "Seed phrase (12 or 24 words)", false, SEED_PHRASE_PATTERN),
                secret("privateKey", "Private key", false),
                secret("passphrase", "Passphrase", false)));

        register(AssetType.E_WALLET, "E-wallet", TransferMechanism.EXECUTOR_DISPOSITION, accountFields(
                List.of("MoMo", "ZaloPay", "ViettelPay", "PayPal", "Other"), "Wallet"));

        register(AssetType.SOCIAL_MEDIA, "Social media", TransferMechanism.EXECUTOR_DISPOSITION, accountFields(
                List.of("Facebook", "Instagram", "TikTok", "X", "LinkedIn", "YouTube", "Other"), "Platform"));

        register(AssetType.EMAIL, "Email account", TransferMechanism.EXECUTOR_DISPOSITION, accountFields(
                List.of("Gmail", "Outlook", "Yahoo", "Apple ID", "Other"), "Provider"));

        register(AssetType.OTHER, "Other", null, List.of(
                text("title", "Title", true),
                textarea("description", "Description", false),
                secret("secret", "Secret", false)));
    }

    private AssetSchemaRegistry() {
    }

    private static List<AssetFieldSpec> accountFields(List<String> platforms, String platformLabel) {
        return List.of(
                select("platform", platformLabel, true, platforms),
                text("usernameOrHandle", "Username / handle", true),
                select("wish", "What should happen to it", true, WISHES),
                text("deviceOrSimLocation", "Where the phone / SIM / 2FA device is kept", false),
                secret("password", "Password", false),
                secret("twoFactorRecoveryCodes", "2FA recovery codes", false));
    }

    private static void register(AssetType type, String label, TransferMechanism mechanism,
                                 List<AssetFieldSpec> fields) {
        SCHEMAS.put(type, new AssetSchema(type, label, mechanism, List.copyOf(fields)));
    }

    public static AssetSchema schemaOf(AssetType type) {
        return SCHEMAS.get(type);
    }

    /** Toàn bộ schema theo thứ tự khai báo của enum (cho GET /api/asset-types). */
    public static List<AssetSchema> all() {
        List<AssetSchema> list = new ArrayList<>();
        for (AssetType type : AssetType.values()) {
            AssetSchema schema = SCHEMAS.get(type);
            if (schema != null) {
                list.add(schema);
            }
        }
        return list;
    }

    /** Tách giá trị đã gửi thành trường công khai và trường bí mật theo schema. */
    public static Split split(AssetType type, Map<String, String> fields) {
        AssetSchema schema = schemaOf(type);
        Map<String, String> publicFields = new LinkedHashMap<>();
        Map<String, String> secretFields = new LinkedHashMap<>();
        for (AssetFieldSpec spec : schema.fields()) {
            String value = fields.get(spec.key());
            if (value == null || value.isBlank()) {
                continue;
            }
            (spec.sensitive() ? secretFields : publicFields).put(spec.key(), value);
        }
        return new Split(publicFields, secretFields);
    }

    public record Split(Map<String, String> publicFields, Map<String, String> secretFields) {
    }
}
