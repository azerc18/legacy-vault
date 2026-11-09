package com.ltld.app.legacyvault.service.assetverificationservice;

import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.AssetVerificationMethod;
import com.ltld.app.legacyvault.enums.VerificationProvider;
import com.ltld.app.legacyvault.exception.VaultException;
import com.ltld.app.legacyvault.utility.OtpHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BankAssetVerifier – C.3.3 xác thực tài sản BANK_ACCOUNT (mô phỏng)")
class BankAssetVerifierTest {

    @Mock private VaultFileHasher fileHasher;

    private BankAccountRules rules;
    private BankAssetVerifier verifier;
    private final OtpHasher otpHasher = new OtpHasher();
    private UUID ownerId;
    private UUID vaultId;
    private Vault vault;

    @BeforeEach
    void setUp() {
        rules = new BankAccountRules();
        rules.setAccountLengths(Map.of("TestBank", List.of(10, 12)));
        verifier = new BankAssetVerifier(rules, fileHasher, otpHasher);
        ownerId = UUID.randomUUID();
        vaultId = UUID.randomUUID();
        User owner = User.builder().id(ownerId).fullName("Nguyễn Văn An").build();
        vault = Vault.builder().id(vaultId).owner(owner).build();
    }

    private DigitalAsset bank(String institution, String number, String holder) {
        String json = "{\"institutionName\":\"" + institution + "\",\"accountNumber\":\"" + number + "\""
                + (holder == null ? "" : ",\"accountHolderName\":\"" + holder + "\"") + "}";
        return DigitalAsset.builder().id(UUID.randomUUID()).vault(vault).assetType(AssetType.BANK_ACCOUNT)
                .assetName("Main account").publicFieldsJson(json).build();
    }

    @Test
    @DisplayName("type() là BANK_ACCOUNT, methods() có đúng 4 cách")
    void typeAndMethods() {
        assertThat(verifier.type()).isEqualTo(AssetType.BANK_ACCOUNT);
        assertThat(verifier.methods()).containsExactly(AssetVerificationMethod.BANK_NAME_LOOKUP_MOCK,
                AssetVerificationMethod.BANK_FORMAT_CHECK, AssetVerificationMethod.STATEMENT_UPLOAD,
                AssetVerificationMethod.MICRO_DEPOSIT_MOCK);
    }

    @Test
    @DisplayName("normalizeName: bỏ dấu, hoa thường, ký tự lạ và khoảng trắng thừa")
    void normalizeName_cases() {
        assertThat(BankAssetVerifier.normalizeName("  Nguyễn  Văn-An ")).isEqualTo("NGUYEN VAN AN");
        assertThat(BankAssetVerifier.normalizeName("Đặng Thị Hồng")).isEqualTo("DANG THI HONG");
        assertThat(BankAssetVerifier.normalizeName(null)).isEmpty();
    }

    @Test
    @DisplayName("BANK_NAME_LOOKUP_MOCK start: thiếu tên chủ tài khoản thì từ chối")
    void nameLookup_start_missingHolder_throws() {
        DigitalAsset asset = bank("TestBank", "1234567890", null);
        assertThatThrownBy(() -> verifier.start(asset, AssetVerificationMethod.BANK_NAME_LOOKUP_MOCK, ownerId))
                .isInstanceOf(VaultException.class);
    }

    @Test
    @DisplayName("BANK_NAME_LOOKUP_MOCK start: có tên thì trả SANDBOX với mode SANDBOX")
    void nameLookup_start_ok() {
        AssetVerifier.StartResult start = verifier.start(bank("TestBank", "1234567890", "NGUYEN VAN AN"),
                AssetVerificationMethod.BANK_NAME_LOOKUP_MOCK, ownerId);
        assertThat(start.provider()).isEqualTo(VerificationProvider.SANDBOX);
        assertThat(start.clientData()).containsEntry("mode", "SANDBOX");
        assertThat(start.challengeHash()).isNull();
    }

    @Test
    @DisplayName("BANK_NAME_LOOKUP_MOCK confirm: tên khớp sau khi bỏ dấu, hoa thường")
    void nameLookup_match() {
        AssetVerifier.VerifyResult result = verifier.confirm(bank("TestBank", "1234567890", "nguyen van an"),
                AssetVerificationMethod.BANK_NAME_LOOKUP_MOCK, Map.of(), ownerId, null);
        assertThat(result.success()).isTrue();
        assertThat(result.provider()).isEqualTo(VerificationProvider.SANDBOX);
        assertThat(result.evidence()).containsEntry("result", "NAME_MATCH");
    }

    @Test
    @DisplayName("BANK_NAME_LOOKUP_MOCK confirm: tên khác thì thất bại và không lộ tên trong thông báo")
    void nameLookup_mismatch() {
        AssetVerifier.VerifyResult result = verifier.confirm(bank("TestBank", "1234567890", "TRAN VAN B"),
                AssetVerificationMethod.BANK_NAME_LOOKUP_MOCK, Map.of(), ownerId, null);
        assertThat(result.success()).isFalse();
        assertThat(result.message()).doesNotContain("TRAN").doesNotContain("NGUYEN");
    }

    @Test
    @DisplayName("BANK_FORMAT_CHECK: độ dài hợp lệ theo ngân hàng thì thành công, khóa ngân hàng so sau khi chuẩn hóa")
    void formatCheck_valid() {
        AssetVerifier.VerifyResult result = verifier.confirm(bank("Test Bank", "123456789012", null),
                AssetVerificationMethod.BANK_FORMAT_CHECK, Map.of(), ownerId, null);
        assertThat(result.success()).isTrue();
        assertThat(result.evidence()).containsEntry("accountLength", "12").doesNotContainValue("123456789012");
    }

    @Test
    @DisplayName("BANK_FORMAT_CHECK: sai độ dài thì thất bại")
    void formatCheck_wrongLength() {
        AssetVerifier.VerifyResult result = verifier.confirm(bank("TestBank", "12345678901", null),
                AssetVerificationMethod.BANK_FORMAT_CHECK, Map.of(), ownerId, null);
        assertThat(result.success()).isFalse();
    }

    @Test
    @DisplayName("BANK_FORMAT_CHECK: ngân hàng chưa có trong bảng thì từ chối ở start và thất bại ở confirm")
    void formatCheck_unknownBank() {
        DigitalAsset asset = bank("Unknown Bank", "1234567890", null);
        assertThatThrownBy(() -> verifier.start(asset, AssetVerificationMethod.BANK_FORMAT_CHECK, ownerId))
                .isInstanceOf(VaultException.class);
        assertThat(verifier.confirm(asset, AssetVerificationMethod.BANK_FORMAT_CHECK, Map.of(), ownerId, null)
                .success()).isFalse();
    }

    @Test
    @DisplayName("STATEMENT_UPLOAD: server băm tệp trong vault, evidence không có tên tệp")
    void statement_success() {
        UUID docId = UUID.randomUUID();
        when(fileHasher.hash(vaultId, docId)).thenReturn(Optional.of(new VaultFileHasher.HashedFile(docId, "abc123")));

        AssetVerifier.VerifyResult result = verifier.confirm(bank("TestBank", "1234567890", null),
                AssetVerificationMethod.STATEMENT_UPLOAD, Map.of("documentId", docId.toString()), ownerId, null);

        assertThat(result.success()).isTrue();
        assertThat(result.provider()).isEqualTo(VerificationProvider.SANDBOX);
        assertThat(result.evidence()).containsEntry("documentId", docId.toString())
                .containsEntry("hashAlgorithm", "SHA-256").containsEntry("sha256", "abc123")
                .doesNotContainKey("fileName");
    }

    @Test
    @DisplayName("STATEMENT_UPLOAD: tài liệu không thuộc vault, UUID sai, thiếu documentId hoặc lỗi đọc đều thất bại")
    void statement_failures() {
        UUID missing = UUID.randomUUID();
        UUID broken = UUID.randomUUID();
        when(fileHasher.hash(vaultId, missing)).thenReturn(Optional.empty());
        when(fileHasher.hash(vaultId, broken)).thenThrow(new IllegalStateException("boom"));
        DigitalAsset asset = bank("TestBank", "1234567890", null);

        for (Map<String, String> payload : List.of(
                Map.of("documentId", missing.toString()),
                Map.of("documentId", broken.toString()),
                Map.of("documentId", "not-a-uuid"),
                Map.<String, String>of())) {
            assertThat(verifier.confirm(asset, AssetVerificationMethod.STATEMENT_UPLOAD, payload, ownerId, null)
                    .success()).isFalse();
        }
    }

    @Test
    @DisplayName("Cách chưa hỗ trợ (OTP_EMAIL) bị từ chối ở start và confirm")
    void unsupportedMethod_throws() {
        DigitalAsset asset = bank("TestBank", "1234567890", null);
        assertThatThrownBy(() -> verifier.start(asset, AssetVerificationMethod.OTP_EMAIL, ownerId))
                .isInstanceOf(VaultException.class);
        assertThatThrownBy(() -> verifier.confirm(asset, AssetVerificationMethod.OTP_EMAIL, Map.of(),
                ownerId, null)).isInstanceOf(VaultException.class);
    }

    @Test
    @DisplayName("methods(): bảng độ dài rỗng thì ẩn BANK_FORMAT_CHECK")
    void methods_emptyLengthTable_hidesFormatCheck() {
        rules.setAccountLengths(Map.of());
        assertThat(verifier.methods()).containsExactly(
                AssetVerificationMethod.BANK_NAME_LOOKUP_MOCK, AssetVerificationMethod.STATEMENT_UPLOAD,
                AssetVerificationMethod.MICRO_DEPOSIT_MOCK);
    }

    private AssetVerifier.VerifyResult confirmMicro(DigitalAsset asset, Map<String, String> payload, String hash) {
        return verifier.confirm(asset, AssetVerificationMethod.MICRO_DEPOSIT_MOCK, payload, ownerId, hash);
    }

    @Test
    @DisplayName("MICRO_DEPOSIT_MOCK start: SANDBOX, số tiền thô 1000-9999 và challengeHash không phải số tiền")
    void microDeposit_start() {
        AssetVerifier.StartResult start = verifier.start(bank("TestBank", "1234567890", null),
                AssetVerificationMethod.MICRO_DEPOSIT_MOCK, ownerId);

        assertThat(start.provider()).isEqualTo(VerificationProvider.SANDBOX);
        assertThat(start.clientData()).containsEntry("mode", "SANDBOX").doesNotContainKey("reference");
        String amount = start.clientData().get("amount");
        assertThat(Integer.parseInt(amount)).isBetween(1000, 9999);
        assertThat(start.challengeHash()).isNotBlank().isNotEqualTo(amount);
    }

    @Test
    @DisplayName("MICRO_DEPOSIT_MOCK confirm: đúng số tiền (có dấu phân cách hoặc VND) thì thành công, evidence không có số tài khoản")
    void microDeposit_correctCode() {
        DigitalAsset asset = bank("TestBank", "1234567890", null);
        AssetVerifier.StartResult start = verifier.start(asset, AssetVerificationMethod.MICRO_DEPOSIT_MOCK, ownerId);
        String amount = start.clientData().get("amount");
        String grouped = amount.charAt(0) + "." + amount.substring(1);

        for (String typed : List.of(amount, grouped, " " + amount + " VND ", grouped + ",00", amount + ".00 VND", grouped + ",0")) {
            AssetVerifier.VerifyResult result = confirmMicro(asset, Map.of("code", typed), start.challengeHash());
            assertThat(result.success()).isTrue();
            assertThat(result.provider()).isEqualTo(VerificationProvider.SANDBOX);
            assertThat(result.evidence()).containsOnly(Map.entry("check", "MICRO_DEPOSIT"), Map.entry("mode", "SIMULATED"));
        }
    }

    @Test
    @DisplayName("MICRO_DEPOSIT_MOCK confirm: sai số tiền, thiếu số tiền, không có chữ số, payload null hoặc chưa start đều thất bại")
    void microDeposit_failures() {
        DigitalAsset asset = bank("TestBank", "1234567890", null);
        String hash = otpHasher.hash(asset.getId(), "1234");

        assertThat(confirmMicro(asset, Map.of("code", "1234"), hash).success()).isTrue();
        assertThat(confirmMicro(asset, Map.of("code", "4321"), hash).success()).isFalse();
        assertThat(confirmMicro(asset, Map.of("code", "abc"), hash).success()).isFalse();
        assertThat(confirmMicro(asset, Map.of("code", "  "), hash).success()).isFalse();
        assertThat(confirmMicro(asset, Map.of(), hash).success()).isFalse();
        assertThat(confirmMicro(asset, null, hash).success()).isFalse();
        assertThat(confirmMicro(asset, Map.of("code", "1234"), null).success()).isFalse();
    }

    @Test
    @DisplayName("depositDigits: bỏ phần thập phân toàn số 0, giữ phần thập phân khác 0")
    void depositDigits_decimals() {
        assertThat(BankAssetVerifier.depositDigits("1.234,00")).isEqualTo("1234");
        assertThat(BankAssetVerifier.depositDigits("1,234.00 VND")).isEqualTo("1234");
        assertThat(BankAssetVerifier.depositDigits("1234,0")).isEqualTo("1234");
        assertThat(BankAssetVerifier.depositDigits("1.234")).isEqualTo("1234");
        assertThat(BankAssetVerifier.depositDigits("1.000")).isEqualTo("1000");
        assertThat(BankAssetVerifier.depositDigits("1.234,5")).isEqualTo("12345");
        assertThat(BankAssetVerifier.depositDigits(null)).isEmpty();
    }
}
