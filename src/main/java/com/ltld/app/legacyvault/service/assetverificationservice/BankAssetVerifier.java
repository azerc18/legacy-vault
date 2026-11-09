package com.ltld.app.legacyvault.service.assetverificationservice;

import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.AssetVerificationMethod;
import com.ltld.app.legacyvault.enums.VerificationProvider;
import com.ltld.app.legacyvault.exception.VaultException;
import com.ltld.app.legacyvault.service.vaultservice.assetschema.FlatJson;
import com.ltld.app.legacyvault.utility.OtpGenerator;
import com.ltld.app.legacyvault.utility.OtpHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

/**
 * C.3.3: Xác thực tài sản BANK_ACCOUNT. Cả bốn cách đều là SANDBOX (nhãn "Mô phỏng"):
 * - BANK_NAME_LOOKUP_MOCK: so tên chủ tài khoản đã nhập với tên chủ két (bỏ dấu, hoa thường); KHÔNG gọi ngân hàng thật.
 * - BANK_FORMAT_CHECK: độ dài số tài khoản theo bảng ngân hàng (BankAccountRules); chỉ chứng minh đúng định dạng.
 * - STATEMENT_UPLOAD: tải sao kê lên kho tài liệu mã hóa, server băm SHA-256; chưa đọc nội dung sao kê.
 * - MICRO_DEPOSIT_MOCK: giả lập một khoản tiền nhỏ kèm mã nội dung, Owner nhập lại mã; không có tiền thật, không gọi ngân hàng,
 *   mã hiện ngay trên màn hình nên không chứng minh Owner sở hữu tài khoản.
 * Bằng chứng không chứa số tài khoản, tên người hay tên tệp.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BankAssetVerifier implements AssetVerifier {

    private static final int CHALLENGE_TTL_MINUTES = 15;
    private static final String REFERENCE_PREFIX = "LV-";

    private final BankAccountRules rules;
    private final VaultFileHasher fileHasher;
    private final OtpGenerator otpGenerator;
    private final OtpHasher otpHasher;

    @Override
    public AssetType type() {
        return AssetType.BANK_ACCOUNT;
    }

    @Override
    public List<AssetVerificationMethod> methods() {
        List<AssetVerificationMethod> methods = new ArrayList<>();
        methods.add(AssetVerificationMethod.BANK_NAME_LOOKUP_MOCK);
        if (rules.hasAnyRule()) {   // bảng rỗng thì ẩn, tránh UI hiện cách luôn trả 400
            methods.add(AssetVerificationMethod.BANK_FORMAT_CHECK);
        }
        methods.add(AssetVerificationMethod.STATEMENT_UPLOAD);
        methods.add(AssetVerificationMethod.MICRO_DEPOSIT_MOCK);
        return List.copyOf(methods);
    }

    @Override
    public StartResult start(DigitalAsset asset, AssetVerificationMethod method, UUID ownerId) {
        Map<String, String> fields = FlatJson.decode(asset.getPublicFieldsJson());
        return switch (method) {
            case BANK_NAME_LOOKUP_MOCK -> {
                if (normalizeName(fields.get("accountHolderName")).isEmpty()) {
                    throw new VaultException(
                            "Add the account holder name to this asset before running the simulated lookup",
                            HttpStatus.BAD_REQUEST);
                }
                yield sandboxStart("Ready to run a simulated account holder lookup");
            }
            case BANK_FORMAT_CHECK -> {
                if (rules.allowedLengths(fields.get("institutionName")).isEmpty()) {
                    throw new VaultException(
                            "This bank is not in the supported list for the format check, please use another method",
                            HttpStatus.BAD_REQUEST);
                }
                yield sandboxStart("Ready to check the account number length for this bank");
            }
            case STATEMENT_UPLOAD -> sandboxStart(
                    "Upload a recent bank statement to the vault, then confirm to store its SHA-256 fingerprint");
            case MICRO_DEPOSIT_MOCK -> startMicroDeposit(asset);
            default -> throw unsupported(method);
        };
    }

    @Override
    public VerifyResult confirm(DigitalAsset asset, AssetVerificationMethod method,
                                Map<String, String> payload, UUID ownerId) {
        return confirm(asset, method, payload, ownerId, null);
    }

    @Override
    public VerifyResult confirm(DigitalAsset asset, AssetVerificationMethod method,
                                Map<String, String> payload, UUID ownerId, String challengeHash) {
        Map<String, String> fields = FlatJson.decode(asset.getPublicFieldsJson());
        Map<String, String> safePayload = payload == null ? Map.of() : payload;
        return switch (method) {
            case BANK_NAME_LOOKUP_MOCK -> confirmNameLookup(asset, fields);
            case BANK_FORMAT_CHECK -> confirmFormatCheck(fields);
            case STATEMENT_UPLOAD -> confirmStatementUpload(asset, safePayload);
            case MICRO_DEPOSIT_MOCK -> confirmMicroDeposit(asset, safePayload, challengeHash);
            default -> throw unsupported(method);
        };
    }

    private StartResult startMicroDeposit(DigitalAsset asset) {
        String code = otpGenerator.generate();
        // Mã mô phỏng chỉ trả về trong clientData, không ghi log
        Map<String, String> clientData = new LinkedHashMap<>();
        clientData.put("mode", "SANDBOX");
        clientData.put("amount", "1.234 VND");
        clientData.put("reference", REFERENCE_PREFIX + code);
        return new StartResult(VerificationProvider.SANDBOX,
                "Simulated micro-deposit created; no real money was moved. Enter the reference code to confirm",
                otpHasher.hash(saltId(asset), code), clientData,
                LocalDateTime.now().plusMinutes(CHALLENGE_TTL_MINUTES));
    }

    private VerifyResult confirmMicroDeposit(DigitalAsset asset, Map<String, String> payload, String challengeHash) {
        String code = payload.get("code");
        if (code == null || code.isBlank()) {
            return fail("Reference code is required");
        }
        if (challengeHash == null || challengeHash.isBlank()) {
            return fail("Verification challenge has not been started");
        }
        // Màn hình hiện "LV-123456" nên Owner có thể gõ cả tiền tố
        String digits = code.trim().toUpperCase(Locale.ROOT).replaceFirst("^" + REFERENCE_PREFIX, "");
        if (!otpHasher.matches(saltId(asset), digits, challengeHash)) {
            return fail("Invalid reference code");
        }
        Map<String, String> evidence = new LinkedHashMap<>();
        evidence.put("check", "MICRO_DEPOSIT");
        evidence.put("mode", "SIMULATED");
        return new VerifyResult(true, VerificationProvider.SANDBOX,
                "Simulated micro-deposit confirmed; no real bank was contacted and account ownership is not proven",
                evidence);
    }

    private VerifyResult confirmNameLookup(DigitalAsset asset, Map<String, String> fields) {
        String holder = normalizeName(fields.get("accountHolderName"));
        String owner = normalizeName(ownerFullName(asset));
        if (holder.isEmpty()) {
            return fail("Account holder name is missing on this asset");
        }
        if (owner.isEmpty()) {
            return fail("Vault owner name is not available");
        }
        if (!holder.equals(owner)) {
            return fail("Account holder name does not match the vault owner's name (simulated lookup)");
        }
        Map<String, String> evidence = new LinkedHashMap<>();
        evidence.put("lookup", "SIMULATED");
        evidence.put("result", "NAME_MATCH");
        return new VerifyResult(true, VerificationProvider.SANDBOX,
                "Simulated lookup: the account holder name matches the vault owner's name; no real bank call was made",
                evidence);
    }

    private VerifyResult confirmFormatCheck(Map<String, String> fields) {
        String bank = fields.get("institutionName");
        Optional<List<Integer>> allowed = rules.allowedLengths(bank);
        if (allowed.isEmpty()) {
            return fail("This bank is not in the supported list for the format check");
        }
        String number = fields.getOrDefault("accountNumber", "").replaceAll("\\s", "");
        if (!number.matches("\\d+") || !allowed.get().contains(number.length())) {
            return fail("The account number length is not valid for this bank");
        }
        Map<String, String> evidence = new LinkedHashMap<>();
        evidence.put("check", "LENGTH_BY_BANK");
        evidence.put("bankKey", BankAccountRules.keyOf(bank));
        evidence.put("accountLength", String.valueOf(number.length()));
        return new VerifyResult(true, VerificationProvider.SANDBOX,
                "The account number length is valid for this bank (format only; ownership is not proven)", evidence);
    }

    private VerifyResult confirmStatementUpload(DigitalAsset asset, Map<String, String> payload) {
        UUID vaultId = asset.getVault() != null ? asset.getVault().getId() : null;
        if (vaultId == null) {
            return fail("Asset is not attached to a vault");
        }
        String docIdStr = payload.get("documentId");
        if (docIdStr == null || docIdStr.isBlank()) {
            return fail("Upload the statement to the vault and send its documentId");
        }
        UUID docId;
        try {
            docId = UUID.fromString(docIdStr.trim());
        } catch (IllegalArgumentException e) {
            return fail("Invalid documentId UUID format");
        }
        try {
            Optional<VaultFileHasher.HashedFile> hashed = fileHasher.hash(vaultId, docId);
            if (hashed.isEmpty()) {
                return fail("Document not found in this vault");
            }
            Map<String, String> evidence = new LinkedHashMap<>();
            evidence.put("documentId", hashed.get().documentId().toString());
            evidence.put("hashAlgorithm", "SHA-256");
            evidence.put("sha256", hashed.get().sha256());
            return new VerifyResult(true, VerificationProvider.SANDBOX,
                    "Statement fingerprint (SHA-256) stored; the statement content was not read", evidence);
        } catch (IllegalStateException e) {
            log.warn("Could not hash statement {} for asset {}: {}", docId, asset.getId(), e.getMessage());
            return fail("Could not read the document, please try again later");
        }
    }

    private StartResult sandboxStart(String message) {
        return new StartResult(VerificationProvider.SANDBOX, message, null,
                Map.of("mode", "SANDBOX"), LocalDateTime.now().plusMinutes(CHALLENGE_TTL_MINUTES));
    }

    private static UUID saltId(DigitalAsset asset) {
        return asset.getId() != null ? asset.getId() : new UUID(0L, 0L);
    }

    private static String ownerFullName(DigitalAsset asset) {
        return asset.getVault() != null && asset.getVault().getOwner() != null
                ? asset.getVault().getOwner().getFullName() : null;
    }

    /** Bỏ dấu, chữ hoa, chỉ giữ chữ cái, gộp khoảng trắng: "Nguyễn  Văn-An" thành "NGUYEN VAN AN". */
    static String normalizeName(String name) {
        if (name == null) {
            return "";
        }
        return BankAccountRules.stripAccents(name).toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z\\s]", " ").trim().replaceAll("\\s+", " ");
    }

    private static VerifyResult fail(String message) {
        return new VerifyResult(false, VerificationProvider.SANDBOX, message, Map.of());
    }

    private static VaultException unsupported(AssetVerificationMethod method) {
        return new VaultException("Unsupported verification method for BANK_ACCOUNT: " + method, HttpStatus.BAD_REQUEST);
    }
}
