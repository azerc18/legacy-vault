package com.ltld.app.legacyvault.service.assetverificationservice;

import com.ltld.app.legacyvault.dto.assetverificationdto.AssetVerificationResponse;
import com.ltld.app.legacyvault.dto.assetverificationdto.AssetVerificationSummary;
import com.ltld.app.legacyvault.dto.assetverificationdto.ConfirmVerificationRequest;
import com.ltld.app.legacyvault.dto.assetverificationdto.StartVerificationRequest;
import com.ltld.app.legacyvault.entity.AssetVerification;
import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.AssetStatus;
import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.AssetVerificationMethod;
import com.ltld.app.legacyvault.enums.AssetVerificationStatus;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.enums.VerificationProvider;
import com.ltld.app.legacyvault.exception.VaultException;
import com.ltld.app.legacyvault.repository.AssetVerificationRepository;
import com.ltld.app.legacyvault.repository.DigitalAssetRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.vaultservice.VaultModificationGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AssetVerificationServiceImpl – nền tảng (start / confirm / summarize)")
class AssetVerificationServiceFlowTest {

    private static final String PUBLIC_JSON =
            "{\"institutionName\":\"MB Bank\",\"accountNumber\":\"0123456789\"}";
    private static final AssetVerificationMethod METHOD = AssetVerificationMethod.BANK_FORMAT_CHECK;

    @Mock private AssetVerificationRepository verificationRepository;
    @Mock private DigitalAssetRepository digitalAssetRepository;
    @Mock private VaultRepository vaultRepository;
    @Mock private ObjectProvider<AssetVerifier> verifiers;
    @Mock private AuditLogService auditLogService;
    @Mock private VaultModificationGuard vaultModificationGuard;
    @Mock private AssetVerifier bankVerifier;

    @InjectMocks private AssetVerificationServiceImpl service;

    private UUID ownerId;
    private UUID vaultId;
    private UUID assetId;
    private Vault mockVault;
    private DigitalAsset mockAsset;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "sandboxEnabled", true);
        ownerId = UUID.randomUUID();
        vaultId = UUID.randomUUID();
        assetId = UUID.randomUUID();
        User owner = User.builder().id(ownerId).email("owner@test.com").build();
        mockVault = Vault.builder().id(vaultId).owner(owner).status(VaultStatus.ACTIVE).build();
        mockAsset = DigitalAsset.builder()
                .id(assetId).vault(mockVault).assetType(AssetType.BANK_ACCOUNT)
                .assetName("MB Bank").publicFieldsJson(PUBLIC_JSON).status(AssetStatus.ACTIVE).build();
    }

    // ---------- helpers ----------

    private void stubOwnedAsset() {
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(mockVault));
        when(digitalAssetRepository.findById(assetId)).thenReturn(Optional.of(mockAsset));
    }

    private void stubBankVerifier() {
        when(bankVerifier.type()).thenReturn(AssetType.BANK_ACCOUNT);
        when(bankVerifier.methods()).thenReturn(List.of(METHOD));
        when(verifiers.orderedStream()).thenAnswer(inv -> Stream.of(bankVerifier));
    }

    private void stubSave() {
        when(verificationRepository.save(any(AssetVerification.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private AssetVerification row(AssetVerificationStatus status, String fingerprint, LocalDateTime expiresAt) {
        return AssetVerification.builder()
                .id(UUID.randomUUID()).asset(mockAsset).method(METHOD).provider(VerificationProvider.REAL)
                .status(status).fingerprint(fingerprint)
                .verifiedAt(LocalDateTime.now().minusDays(1)).expiresAt(expiresAt).build();
    }

    private StartVerificationRequest startRequest(AssetVerificationMethod method) {
        StartVerificationRequest r = new StartVerificationRequest();
        r.setMethod(method);
        return r;
    }

    private ConfirmVerificationRequest confirmRequest(Map<String, String> payload) {
        ConfirmVerificationRequest r = new ConfirmVerificationRequest();
        r.setMethod(METHOD);
        r.setPayload(payload);
        return r;
    }

    private AssetVerification captureSaved() {
        ArgumentCaptor<AssetVerification> captor = ArgumentCaptor.forClass(AssetVerification.class);
        verify(verificationRepository).save(captor.capture());
        return captor.getValue();
    }

    private void verifyAudit(AuditAction action, AuditResult result) {
        verify(auditLogService).log(eq(action), eq(result), eq(ownerId), eq("owner@test.com"),
                eq(vaultId), eq("DigitalAsset"), eq(assetId.toString()), eq(METHOD.name()));
    }

    // =========================================================
    // summarize: trạng thái hiệu lực
    // =========================================================

    @Test
    @DisplayName("summarize: chưa có dòng nào → SELF_DECLARED")
    void summarize_noRows_selfDeclared() {
        when(verificationRepository.findLatestByAssetIds(any())).thenReturn(List.of());

        Map<UUID, AssetVerificationSummary> result = service.summarize(List.of(mockAsset));

        assertThat(result.get(assetId).status()).isEqualTo(AssetVerificationStatus.SELF_DECLARED);
        assertThat(result.get(assetId).method()).isNull();
    }

    @Test
    @DisplayName("summarize: đã xác thực và dữ liệu không đổi → VERIFIED")
    void summarize_verifiedAndUnchanged_verified() {
        AssetVerification verified = row(AssetVerificationStatus.VERIFIED,
                AssetFingerprint.compute(mockAsset), LocalDateTime.now().plusDays(100));
        when(verificationRepository.findLatestByAssetIds(any())).thenReturn(List.of(verified));

        AssetVerificationSummary s = service.summarize(List.of(mockAsset)).get(assetId);

        assertThat(s.status()).isEqualTo(AssetVerificationStatus.VERIFIED);
        assertThat(s.method()).isEqualTo(METHOD);
        assertThat(s.provider()).isEqualTo(VerificationProvider.REAL);
    }

    @Test
    @DisplayName("summarize: Owner sửa trường danh tính (số tài khoản) → tự về SELF_DECLARED")
    void summarize_identityFieldEdited_backToSelfDeclared() {
        AssetVerification verified = row(AssetVerificationStatus.VERIFIED,
                AssetFingerprint.compute(mockAsset), LocalDateTime.now().plusDays(100));
        when(verificationRepository.findLatestByAssetIds(any())).thenReturn(List.of(verified));

        mockAsset.setPublicFieldsJson("{\"institutionName\":\"MB Bank\",\"accountNumber\":\"9999999999\"}");

        AssetVerificationSummary s = service.summarize(List.of(mockAsset)).get(assetId);

        assertThat(s.status()).isEqualTo(AssetVerificationStatus.SELF_DECLARED);
        assertThat(s.method()).isNull();
    }

    @Test
    @DisplayName("summarize: Owner chỉ sửa giá trị ước tính → vẫn VERIFIED")
    void summarize_nonIdentityFieldEdited_stillVerified() {
        AssetVerification verified = row(AssetVerificationStatus.VERIFIED,
                AssetFingerprint.compute(mockAsset), LocalDateTime.now().plusDays(100));
        when(verificationRepository.findLatestByAssetIds(any())).thenReturn(List.of(verified));

        mockAsset.setPublicFieldsJson(
                "{\"institutionName\":\"MB Bank\",\"accountNumber\":\"0123456789\",\"estimatedValue\":\"5000\"}");

        assertThat(service.summarize(List.of(mockAsset)).get(assetId).status())
                .isEqualTo(AssetVerificationStatus.VERIFIED);
    }

    @Test
    @DisplayName("summarize: quá hạn → EXPIRED")
    void summarize_expired() {
        AssetVerification verified = row(AssetVerificationStatus.VERIFIED,
                AssetFingerprint.compute(mockAsset), LocalDateTime.now().minusDays(1));
        when(verificationRepository.findLatestByAssetIds(any())).thenReturn(List.of(verified));

        assertThat(service.summarize(List.of(mockAsset)).get(assetId).status())
                .isEqualTo(AssetVerificationStatus.EXPIRED);
    }

    @Test
    @DisplayName("summarize: danh sách rỗng → không gọi repository")
    void summarize_empty_noQuery() {
        assertThat(service.summarize(List.of())).isEmpty();
        verifyNoInteractions(verificationRepository);
    }

    @Test
    @DisplayName("summarize: nhiều tài sản chỉ tốn 1 query (không N+1)")
    void summarize_manyAssets_singleQuery() {
        DigitalAsset other = DigitalAsset.builder().id(UUID.randomUUID()).vault(mockVault)
                .assetType(AssetType.DOCUMENT).assetName("Sổ đỏ").status(AssetStatus.ACTIVE).build();
        when(verificationRepository.findLatestByAssetIds(any())).thenReturn(List.of());

        Map<UUID, AssetVerificationSummary> result = service.summarize(List.of(mockAsset, other));

        assertThat(result).hasSize(2);
        verify(verificationRepository, times(1)).findLatestByAssetIds(any());
    }

    // =========================================================
    // methodsByType
    // =========================================================

    @Test
    @DisplayName("methodsByType: gom cách xác thực theo loại tài sản")
    void methodsByType_groupsByType() {
        stubBankVerifier();

        Map<AssetType, List<AssetVerificationMethod>> result = service.methodsByType();

        assertThat(result).containsOnlyKeys(AssetType.BANK_ACCOUNT);
        assertThat(result.get(AssetType.BANK_ACCOUNT)).containsExactly(METHOD);
    }

    @Test
    @DisplayName("methodsByType: chưa có verifier nào → rỗng")
    void methodsByType_noVerifiers_empty() {
        when(verifiers.orderedStream()).thenReturn(Stream.empty());
        assertThat(service.methodsByType()).isEmpty();
    }

    // =========================================================
    // start
    // =========================================================

    @Test
    @DisplayName("start: thành công → lưu dòng PENDING (kèm challengeHash, dấu vân tay) và ghi audit STARTED")
    void start_success_savesPending() {
        stubOwnedAsset();
        stubBankVerifier();
        stubSave();
        when(bankVerifier.start(mockAsset, METHOD, ownerId)).thenReturn(new AssetVerifier.StartResult(
                VerificationProvider.REAL, "Code sent", "hash123", Map.of("hint", "check mail"),
                LocalDateTime.now().plusMinutes(10)));

        AssetVerificationResponse response = service.start(ownerId, vaultId, assetId, startRequest(METHOD));

        AssetVerification saved = captureSaved();
        assertThat(saved.getStatus()).isEqualTo(AssetVerificationStatus.PENDING);
        assertThat(saved.getChallengeHash()).isEqualTo("hash123");
        assertThat(saved.getFingerprint()).isEqualTo(AssetFingerprint.compute(mockAsset));
        assertThat(response.getStatus()).isEqualTo(AssetVerificationStatus.PENDING);
        assertThat(response.getMessage()).isEqualTo("Code sent");
        assertThat(response.getClientData()).containsEntry("hint", "check mail");
        verifyAudit(AuditAction.ASSET_VERIFICATION_STARTED, AuditResult.SUCCESS);
    }

    @Test
    @DisplayName("start: cách xác thực không hỗ trợ cho loại tài sản → 400, không gọi verifier")
    void start_unsupportedMethod_throws400() {
        stubOwnedAsset();
        stubBankVerifier();

        assertThatThrownBy(() -> service.start(ownerId, vaultId, assetId,
                startRequest(AssetVerificationMethod.SCREENSHOT_UPLOAD)))
                .isInstanceOf(VaultException.class)
                .extracting("status").isEqualTo(HttpStatus.BAD_REQUEST);
        verify(bankVerifier, never()).start(any(), any(), any());
    }

    @Test
    @DisplayName("start: tài sản đã xác thực còn hiệu lực → 409")
    void start_alreadyVerified_throws409() {
        stubOwnedAsset();
        when(verificationRepository.findFirstByAssetIdOrderByCreatedAtDesc(assetId)).thenReturn(Optional.of(
                row(AssetVerificationStatus.VERIFIED, AssetFingerprint.compute(mockAsset),
                        LocalDateTime.now().plusDays(100))));

        assertThatThrownBy(() -> service.start(ownerId, vaultId, assetId, startRequest(METHOD)))
                .isInstanceOf(VaultException.class)
                .extracting("status").isEqualTo(HttpStatus.CONFLICT);
        verify(verificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("start: verifier trả SANDBOX nhưng cờ sandbox tắt → 400, không lưu")
    void start_sandboxDisabled_throws400() {
        ReflectionTestUtils.setField(service, "sandboxEnabled", false);
        stubOwnedAsset();
        stubBankVerifier();
        when(bankVerifier.start(mockAsset, METHOD, ownerId)).thenReturn(new AssetVerifier.StartResult(
                VerificationProvider.SANDBOX, "Mock", "h", Map.of(), null));

        assertThatThrownBy(() -> service.start(ownerId, vaultId, assetId, startRequest(METHOD)))
                .isInstanceOf(VaultException.class)
                .extracting("status").isEqualTo(HttpStatus.BAD_REQUEST);
        verify(verificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("start: két không được phép sửa (guard ném lỗi) → lỗi được giữ nguyên, không lưu")
    void start_vaultNotModifiable_propagates() {
        when(vaultRepository.findById(vaultId)).thenReturn(Optional.of(mockVault));
        doThrow(new VaultException("locked", HttpStatus.CONFLICT))
                .when(vaultModificationGuard).ensureModifiable(mockVault);

        assertThatThrownBy(() -> service.start(ownerId, vaultId, assetId, startRequest(METHOD)))
                .isInstanceOf(VaultException.class)
                .extracting("status").isEqualTo(HttpStatus.CONFLICT);
        verifyNoInteractions(verificationRepository);
    }

    // =========================================================
    // confirm
    // =========================================================

    @Test
    @DisplayName("confirm: thành công (REAL, không có bước start) → VERIFIED, hết hạn sau ~180 ngày, audit VERIFIED")
    void confirm_realSuccess_verified() {
        stubOwnedAsset();
        stubBankVerifier();
        stubSave();
        Map<String, String> payload = Map.of("code", "123456");
        when(bankVerifier.confirm(eq(mockAsset), eq(METHOD), eq(payload), eq(ownerId), any()))
                .thenReturn(new AssetVerifier.VerifyResult(true, VerificationProvider.REAL, "OK", Map.of("src", "mock")));

        AssetVerificationResponse response = service.confirm(ownerId, vaultId, assetId, confirmRequest(payload));

        AssetVerification saved = captureSaved();
        assertThat(saved.getStatus()).isEqualTo(AssetVerificationStatus.VERIFIED);
        assertThat(saved.getVerifiedAt()).isNotNull();
        assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now().plusDays(179));
        assertThat(saved.getFingerprint()).isEqualTo(AssetFingerprint.compute(mockAsset));
        assertThat(saved.getChallengeHash()).isNull();
        assertThat(response.getStatus()).isEqualTo(AssetVerificationStatus.VERIFIED);
        verifyAudit(AuditAction.ASSET_VERIFICATION_VERIFIED, AuditResult.SUCCESS);
    }

    @Test
    @DisplayName("confirm: verifier mô phỏng (SANDBOX) → VERIFIED_SANDBOX")
    void confirm_sandboxSuccess_verifiedSandbox() {
        stubOwnedAsset();
        stubBankVerifier();
        stubSave();
        Map<String, String> payload = Map.of("code", "123456");
        when(bankVerifier.confirm(eq(mockAsset), eq(METHOD), eq(payload), eq(ownerId), any()))
                .thenReturn(new AssetVerifier.VerifyResult(true, VerificationProvider.SANDBOX, "OK", null));

        service.confirm(ownerId, vaultId, assetId, confirmRequest(payload));

        AssetVerification saved = captureSaved();
        assertThat(saved.getStatus()).isEqualTo(AssetVerificationStatus.VERIFIED_SANDBOX);
        assertThat(saved.getProvider()).isEqualTo(VerificationProvider.SANDBOX);
    }

    @Test
    @DisplayName("confirm: có dòng PENDING → truyền challengeHash cho verifier và dùng lại dòng đó")
    void confirm_pending_passesChallengeHashAndReusesRow() {
        stubOwnedAsset();
        stubBankVerifier();
        stubSave();
        AssetVerification pending = row(AssetVerificationStatus.PENDING,
                AssetFingerprint.compute(mockAsset), LocalDateTime.now().plusMinutes(5));
        pending.setChallengeHash("hash123");
        pending.setVerifiedAt(null);
        when(verificationRepository.findFirstByAssetIdOrderByCreatedAtDesc(assetId)).thenReturn(Optional.of(pending));
        Map<String, String> payload = Map.of("code", "123456");
        when(bankVerifier.confirm(mockAsset, METHOD, payload, ownerId, "hash123"))
                .thenReturn(new AssetVerifier.VerifyResult(true, VerificationProvider.REAL, "OK", Map.of()));

        service.confirm(ownerId, vaultId, assetId, confirmRequest(payload));

        AssetVerification saved = captureSaved();
        assertThat(saved.getId()).isEqualTo(pending.getId());
        assertThat(saved.getStatus()).isEqualTo(AssetVerificationStatus.VERIFIED);
        assertThat(saved.getChallengeHash()).isNull();
    }

    @Test
    @DisplayName("confirm: verifier báo sai → lưu dòng FAILED, ghi audit FAILED, KHÔNG ném lỗi")
    void confirm_failure_savesFailedRow() {
        stubOwnedAsset();
        stubBankVerifier();
        stubSave();
        Map<String, String> payload = Map.of("code", "000000");
        when(bankVerifier.confirm(eq(mockAsset), eq(METHOD), eq(payload), eq(ownerId), any()))
                .thenReturn(new AssetVerifier.VerifyResult(false, VerificationProvider.REAL, "Wrong code", Map.of()));

        AssetVerificationResponse response = service.confirm(ownerId, vaultId, assetId, confirmRequest(payload));

        AssetVerification saved = captureSaved();
        assertThat(saved.getStatus()).isEqualTo(AssetVerificationStatus.FAILED);
        assertThat(saved.getVerifiedAt()).isNull();
        assertThat(response.getStatus()).isEqualTo(AssetVerificationStatus.FAILED);
        assertThat(response.getMessage()).isEqualTo("Wrong code");
        verifyAudit(AuditAction.ASSET_VERIFICATION_FAILED, AuditResult.FAILURE);
    }

    @Test
    @DisplayName("confirm: thử thách PENDING đã hết hạn → 400, không gọi verifier")
    void confirm_pendingExpired_throws400() {
        stubOwnedAsset();
        stubBankVerifier();
        AssetVerification pending = row(AssetVerificationStatus.PENDING,
                AssetFingerprint.compute(mockAsset), LocalDateTime.now().minusMinutes(1));
        when(verificationRepository.findFirstByAssetIdOrderByCreatedAtDesc(assetId)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.confirm(ownerId, vaultId, assetId, confirmRequest(Map.of())))
                .isInstanceOf(VaultException.class)
                .extracting("status").isEqualTo(HttpStatus.BAD_REQUEST);
        verify(bankVerifier, never()).confirm(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("confirm: tài sản bị sửa trường danh tính sau khi start → 400, phải start lại")
    void confirm_assetChangedSinceStart_throws400() {
        stubOwnedAsset();
        stubBankVerifier();
        AssetVerification pending = row(AssetVerificationStatus.PENDING,
                AssetFingerprint.compute(mockAsset), LocalDateTime.now().plusMinutes(5));
        when(verificationRepository.findFirstByAssetIdOrderByCreatedAtDesc(assetId)).thenReturn(Optional.of(pending));

        mockAsset.setPublicFieldsJson("{\"institutionName\":\"MB Bank\",\"accountNumber\":\"9999999999\"}");

        assertThatThrownBy(() -> service.confirm(ownerId, vaultId, assetId, confirmRequest(Map.of())))
                .isInstanceOf(VaultException.class)
                .extracting("status").isEqualTo(HttpStatus.BAD_REQUEST);
        verify(bankVerifier, never()).confirm(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("confirm: tài sản đã xác thực còn hiệu lực → 409")
    void confirm_alreadyVerified_throws409() {
        stubOwnedAsset();
        when(verificationRepository.findFirstByAssetIdOrderByCreatedAtDesc(assetId)).thenReturn(Optional.of(
                row(AssetVerificationStatus.VERIFIED, AssetFingerprint.compute(mockAsset),
                        LocalDateTime.now().plusDays(100))));

        assertThatThrownBy(() -> service.confirm(ownerId, vaultId, assetId, confirmRequest(Map.of())))
                .isInstanceOf(VaultException.class)
                .extracting("status").isEqualTo(HttpStatus.CONFLICT);
        verify(verificationRepository, never()).save(any());
    }
}
