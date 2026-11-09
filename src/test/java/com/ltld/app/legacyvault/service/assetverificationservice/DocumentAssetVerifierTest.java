package com.ltld.app.legacyvault.service.assetverificationservice;

import com.ltld.app.legacyvault.entity.*;
import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.AssetVerificationMethod;
import com.ltld.app.legacyvault.enums.DmsConfirmerStatus;
import com.ltld.app.legacyvault.enums.VerificationProvider;
import com.ltld.app.legacyvault.exception.VaultException;
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
import com.ltld.app.legacyvault.repository.DmsConfirmerRepository;
import com.ltld.app.legacyvault.repository.LegalDocumentRepository;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import com.ltld.app.legacyvault.service.fileservice.FileStorageService;
import com.ltld.app.legacyvault.utility.EmailSender;
import com.ltld.app.legacyvault.utility.OtpGenerator;
import com.ltld.app.legacyvault.utility.OtpHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentAssetVerifier – C.3.7 xác thực tài sản DOCUMENT (FILE_HASH, SECOND_PERSON_CONFIRM, DOCUMENT_TIMESTAMP)")
class DocumentAssetVerifierTest {

    @Mock private LegalDocumentRepository legalDocumentRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private CryptoService cryptoService;
    @Mock private DmsConfigRepository dmsConfigRepository;
    @Mock private DmsConfirmerRepository dmsConfirmerRepository;
    @Mock private OtpGenerator otpGenerator;
    @Mock private EmailSender emailSender;

    private DocumentAssetVerifier verifier;
    private UUID ownerId;
    private UUID vaultId;
    private Vault vault;

    @BeforeEach
    void setUp() {
        verifier = new DocumentAssetVerifier(legalDocumentRepository, fileStorageService, cryptoService,
                dmsConfigRepository, dmsConfirmerRepository, otpGenerator, new OtpHasher(), emailSender);
        ownerId = UUID.randomUUID();
        vaultId = UUID.randomUUID();
        vault = Vault.builder().id(vaultId).build();
    }

    private DigitalAsset documentAsset(UUID linkedGroupId) {
        return DigitalAsset.builder()
                .id(UUID.randomUUID())
                .vault(vault)
                .assetType(AssetType.DOCUMENT)
                .assetName("Land title")
                .publicFieldsJson("{\"documentTitle\":\"Land title\",\"location\":\"Home safe\"}")
                .linkedDocumentGroupId(linkedGroupId)
                .build();
    }

    private LegalDocument document(UUID docId) {
        return LegalDocument.builder()
                .id(docId)
                .vault(vault)
                .fileName("land-title.pdf")
                .fileUrlEncrypted("enc://bucket/land-title.pdf")
                .build();
    }

    private static String sha256(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }

    @Test
    @DisplayName("methods(): FILE_HASH, SECOND_PERSON_CONFIRM và DOCUMENT_TIMESTAMP, type() là DOCUMENT")
    void methodsAndType() {
        assertThat(verifier.type()).isEqualTo(AssetType.DOCUMENT);
        assertThat(verifier.methods()).containsExactly(
                AssetVerificationMethod.FILE_HASH,
                AssetVerificationMethod.SECOND_PERSON_CONFIRM,
                AssetVerificationMethod.DOCUMENT_TIMESTAMP);
    }

    // ---------- FILE_HASH ----------

    @Test
    @DisplayName("FILE_HASH start: trả REAL, không có challengeHash")
    void fileHash_start() {
        AssetVerifier.StartResult start = verifier.start(documentAsset(null), AssetVerificationMethod.FILE_HASH, ownerId);

        assertThat(start.provider()).isEqualTo(VerificationProvider.REAL);
        assertThat(start.challengeHash()).isNull();
        assertThat(start.clientData()).containsEntry("documentTitle", "Land title");
        assertThat(start.challengeExpiresAt()).isNotNull();
    }

    @Test
    @DisplayName("FILE_HASH confirm: server tự băm nội dung tệp trong vault, trả REAL")
    void fileHash_withDocumentId_success() throws Exception {
        UUID docId = UUID.randomUUID();
        when(legalDocumentRepository.findByIdAndVaultId(docId, vaultId)).thenReturn(Optional.of(document(docId)));
        byte[] cipher = {1, 2};
        byte[] plain = "pdf-bytes".getBytes(StandardCharsets.UTF_8);
        when(fileStorageService.readFile("enc://bucket/land-title.pdf")).thenReturn(cipher);
        when(cryptoService.decryptBytes(cipher)).thenReturn(plain);

        AssetVerifier.VerifyResult result = verifier.confirm(documentAsset(null), AssetVerificationMethod.FILE_HASH,
                Map.of("documentId", docId.toString()), ownerId, null);

        assertThat(result.success()).isTrue();
        assertThat(result.provider()).isEqualTo(VerificationProvider.REAL);
        assertThat(result.evidence())
                .containsEntry("documentId", docId.toString())
                .containsEntry("fileName", "land-title.pdf")
                .containsEntry("hashAlgorithm", "SHA-256")
                .containsEntry("sha256", sha256(plain));
    }

    @Test
    @DisplayName("FILE_HASH confirm: không gửi documentId thì dùng nhóm tài liệu đã gắn với tài sản")
    void fileHash_fallbackToLinkedDocumentGroup() throws Exception {
        UUID groupId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        when(legalDocumentRepository.findFirstByDocumentGroupIdOrderByVersionDesc(groupId))
                .thenReturn(Optional.of(document(docId)));
        byte[] cipher = {3};
        byte[] plain = "v2".getBytes(StandardCharsets.UTF_8);
        when(fileStorageService.readFile("enc://bucket/land-title.pdf")).thenReturn(cipher);
        when(cryptoService.decryptBytes(cipher)).thenReturn(plain);

        AssetVerifier.VerifyResult result = verifier.confirm(documentAsset(groupId),
                AssetVerificationMethod.FILE_HASH, Map.of(), ownerId, null);

        assertThat(result.success()).isTrue();
        assertThat(result.evidence()).containsEntry("sha256", sha256(plain));
    }

    @Test
    @DisplayName("FILE_HASH confirm: tài liệu ở vault khác thì thất bại")
    void fileHash_documentFromOtherVault_fails() {
        UUID docId = UUID.randomUUID();
        when(legalDocumentRepository.findByIdAndVaultId(docId, vaultId)).thenReturn(Optional.empty());

        AssetVerifier.VerifyResult result = verifier.confirm(documentAsset(null), AssetVerificationMethod.FILE_HASH,
                Map.of("documentId", docId.toString()), ownerId, null);

        assertThat(result.success()).isFalse();
    }

    @Test
    @DisplayName("FILE_HASH confirm: chỉ gửi chuỗi sha256 do client tự tính thì thất bại")
    void fileHash_clientHashOnly_fails() {
        AssetVerifier.VerifyResult result = verifier.confirm(documentAsset(null), AssetVerificationMethod.FILE_HASH,
                Map.of("sha256", "a".repeat(64)), ownerId, null);

        assertThat(result.success()).isFalse();
    }

    @Test
    @DisplayName("FILE_HASH confirm: documentId sai định dạng thì thất bại, không ném lỗi")
    void fileHash_invalidUuid_fails() {
        AssetVerifier.VerifyResult result = verifier.confirm(documentAsset(null), AssetVerificationMethod.FILE_HASH,
                Map.of("documentId", "not-a-uuid"), ownerId, null);

        assertThat(result.success()).isFalse();
    }

    // ---------- DOCUMENT_TIMESTAMP (Việc 46) ----------

    @Test
    @DisplayName("DOCUMENT_TIMESTAMP start: trả SANDBOX, không có challengeHash")
    void documentTimestamp_start() {
        AssetVerifier.StartResult start = verifier.start(documentAsset(null), AssetVerificationMethod.DOCUMENT_TIMESTAMP, ownerId);

        assertThat(start.provider()).isEqualTo(VerificationProvider.SANDBOX);
        assertThat(start.challengeHash()).isNull();
        assertThat(start.clientData()).containsEntry("documentTitle", "Land title");
        assertThat(start.challengeExpiresAt()).isNotNull();
    }

    @Test
    @DisplayName("DOCUMENT_TIMESTAMP confirm: server tự băm nội dung tệp trong vault, trả SANDBOX kèm timestampedAt và mode SIMULATED")
    void documentTimestamp_withDocumentId_success() throws Exception {
        UUID docId = UUID.randomUUID();
        when(legalDocumentRepository.findByIdAndVaultId(docId, vaultId)).thenReturn(Optional.of(document(docId)));
        byte[] cipher = {10, 20};
        byte[] plain = "timestamp-pdf-bytes".getBytes(StandardCharsets.UTF_8);
        when(fileStorageService.readFile("enc://bucket/land-title.pdf")).thenReturn(cipher);
        when(cryptoService.decryptBytes(cipher)).thenReturn(plain);

        AssetVerifier.VerifyResult result = verifier.confirm(documentAsset(null), AssetVerificationMethod.DOCUMENT_TIMESTAMP,
                Map.of("documentId", docId.toString()), ownerId, null);

        assertThat(result.success()).isTrue();
        assertThat(result.provider()).isEqualTo(VerificationProvider.SANDBOX);
        assertThat(result.evidence())
                .containsEntry("documentId", docId.toString())
                .containsEntry("sha256", sha256(plain))
                .containsEntry("mode", "SIMULATED")
                .containsKey("timestampedAt");
        assertThat(Instant.parse(result.evidence().get("timestampedAt"))).isNotNull();
    }

    @Test
    @DisplayName("DOCUMENT_TIMESTAMP confirm: fallback nhóm tài liệu gắn với tài sản khi không truyền documentId")
    void documentTimestamp_fallbackToLinkedDocumentGroup() throws Exception {
        UUID groupId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        when(legalDocumentRepository.findFirstByDocumentGroupIdOrderByVersionDesc(groupId))
                .thenReturn(Optional.of(document(docId)));
        byte[] cipher = {30};
        byte[] plain = "linked-v2-bytes".getBytes(StandardCharsets.UTF_8);
        when(fileStorageService.readFile("enc://bucket/land-title.pdf")).thenReturn(cipher);
        when(cryptoService.decryptBytes(cipher)).thenReturn(plain);

        AssetVerifier.VerifyResult result = verifier.confirm(documentAsset(groupId),
                AssetVerificationMethod.DOCUMENT_TIMESTAMP, Map.of(), ownerId, null);

        assertThat(result.success()).isTrue();
        assertThat(result.provider()).isEqualTo(VerificationProvider.SANDBOX);
        assertThat(result.evidence())
                .containsEntry("sha256", sha256(plain))
                .containsEntry("mode", "SIMULATED")
                .containsKey("timestampedAt");
    }

    @Test
    @DisplayName("DOCUMENT_TIMESTAMP confirm: tài liệu ở vault khác thì thất bại (SANDBOX)")
    void documentTimestamp_documentFromOtherVault_fails() {
        UUID docId = UUID.randomUUID();
        when(legalDocumentRepository.findByIdAndVaultId(docId, vaultId)).thenReturn(Optional.empty());

        AssetVerifier.VerifyResult result = verifier.confirm(documentAsset(null), AssetVerificationMethod.DOCUMENT_TIMESTAMP,
                Map.of("documentId", docId.toString()), ownerId, null);

        assertThat(result.success()).isFalse();
        assertThat(result.provider()).isEqualTo(VerificationProvider.SANDBOX);
    }

    @Test
    @DisplayName("DOCUMENT_TIMESTAMP confirm: chỉ gửi chuỗi sha256 do client tự tính thì thất bại (SANDBOX)")
    void documentTimestamp_clientHashOnly_fails() {
        AssetVerifier.VerifyResult result = verifier.confirm(documentAsset(null), AssetVerificationMethod.DOCUMENT_TIMESTAMP,
                Map.of("sha256", "a".repeat(64)), ownerId, null);

        assertThat(result.success()).isFalse();
        assertThat(result.provider()).isEqualTo(VerificationProvider.SANDBOX);
    }

    @Test
    @DisplayName("DOCUMENT_TIMESTAMP confirm: documentId sai định dạng thì thất bại (SANDBOX), không ném lỗi")
    void documentTimestamp_invalidUuid_fails() {
        AssetVerifier.VerifyResult result = verifier.confirm(documentAsset(null), AssetVerificationMethod.DOCUMENT_TIMESTAMP,
                Map.of("documentId", "not-a-valid-uuid"), ownerId, null);

        assertThat(result.success()).isFalse();
        assertThat(result.provider()).isEqualTo(VerificationProvider.SANDBOX);
    }

    @Test
    @DisplayName("DOCUMENT_TIMESTAMP confirm: không có documentId và không có linkedDocumentGroupId thì thất bại (SANDBOX)")
    void documentTimestamp_noDocIdNoLinkedGroup_fails() {
        AssetVerifier.VerifyResult result = verifier.confirm(documentAsset(null), AssetVerificationMethod.DOCUMENT_TIMESTAMP,
                Map.of(), ownerId, null);

        assertThat(result.success()).isFalse();
        assertThat(result.provider()).isEqualTo(VerificationProvider.SANDBOX);
    }

    @Test
    @DisplayName("DOCUMENT_TIMESTAMP confirm: asset chưa gắn vault thì thất bại (SANDBOX)")
    void documentTimestamp_noVault_fails() {
        DigitalAsset assetNoVault = DigitalAsset.builder()
                .id(UUID.randomUUID())
                .vault(null)
                .assetType(AssetType.DOCUMENT)
                .build();

        AssetVerifier.VerifyResult result = verifier.confirm(assetNoVault, AssetVerificationMethod.DOCUMENT_TIMESTAMP,
                Map.of("documentId", UUID.randomUUID().toString()), ownerId, null);

        assertThat(result.success()).isFalse();
        assertThat(result.provider()).isEqualTo(VerificationProvider.SANDBOX);
    }

    @Test
    @DisplayName("Cách không thuộc DOCUMENT (BANK_FORMAT_CHECK) bị từ chối ở start và confirm")
    void unsupportedMethod_throws() {
        DigitalAsset asset = documentAsset(null);

        assertThatThrownBy(() -> verifier.start(asset, AssetVerificationMethod.BANK_FORMAT_CHECK, ownerId))
                .isInstanceOf(VaultException.class);
        assertThatThrownBy(() -> verifier.confirm(asset, AssetVerificationMethod.BANK_FORMAT_CHECK,
                Map.of(), ownerId, null)).isInstanceOf(VaultException.class);
    }

    // ---------- SECOND_PERSON_CONFIRM ----------

    private static DmsConfirmer confirmer(String email, DmsConfirmerStatus status) {
        return DmsConfirmer.builder().id(UUID.randomUUID()).name("Witness").email(email).status(status).build();
    }

    private void givenConfirmers(DmsConfirmer... confirmers) {
        UUID configId = UUID.randomUUID();
        when(dmsConfigRepository.findByOwnerId(ownerId))
                .thenReturn(Optional.of(DmsConfig.builder().id(configId).build()));
        when(dmsConfirmerRepository.findAllByDmsConfigIdOrderByCreatedAtAsc(configId))
                .thenReturn(List.of(confirmers));
    }

    @Test
    @DisplayName("SECOND_PERSON_CONFIRM start: Owner chưa có cấu hình DMS thì từ chối, không gửi mail")
    void secondPerson_start_noDmsConfig_throws() {
        when(dmsConfigRepository.findByOwnerId(ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> verifier.start(documentAsset(null),
                AssetVerificationMethod.SECOND_PERSON_CONFIRM, ownerId)).isInstanceOf(VaultException.class);
        verifyNoInteractions(emailSender);
    }

    @Test
    @DisplayName("SECOND_PERSON_CONFIRM start: chỉ có confirmer đã báo không liên lạc được thì từ chối")
    void secondPerson_start_onlyUnreachableConfirmers_throws() {
        givenConfirmers(confirmer("gone@x.com", DmsConfirmerStatus.CONFIRMED_UNREACHABLE));

        assertThatThrownBy(() -> verifier.start(documentAsset(null),
                AssetVerificationMethod.SECOND_PERSON_CONFIRM, ownerId)).isInstanceOf(VaultException.class);
        verifyNoInteractions(emailSender);
    }

    @Test
    @DisplayName("SECOND_PERSON_CONFIRM start: gửi mã tới confirmer còn dùng được, bỏ qua UNREACHABLE, che email")
    void secondPerson_start_sendsToUsableConfirmersOnly() {
        givenConfirmers(
                confirmer("witness@x.com", DmsConfirmerStatus.ACTIVE),
                confirmer("friend@x.com", DmsConfirmerStatus.CONFIRMED_ALIVE),
                confirmer("gone@x.com", DmsConfirmerStatus.CONFIRMED_UNREACHABLE));
        when(otpGenerator.generate()).thenReturn("482910");

        AssetVerifier.StartResult start = verifier.start(documentAsset(null),
                AssetVerificationMethod.SECOND_PERSON_CONFIRM, ownerId);

        assertThat(start.provider()).isEqualTo(VerificationProvider.REAL);
        assertThat(start.challengeHash()).hasSize(64);
        assertThat(start.challengeExpiresAt()).isNotNull();
        assertThat(start.clientData().get("maskedEmail"))
                .contains("w***s@x.com", "f***d@x.com")
                .doesNotContain("witness@x.com", "friend@x.com", "gone");
        verify(emailSender).sendSecondPersonConfirmEmail("witness@x.com", "482910", 60);
        verify(emailSender).sendSecondPersonConfirmEmail("friend@x.com", "482910", 60);
        verify(emailSender, never()).sendSecondPersonConfirmEmail(eq("gone@x.com"), any(), anyInt());
    }

    @Test
    @DisplayName("SECOND_PERSON_CONFIRM confirm: đúng mã thì thành công, REAL, evidence không chứa email")
    void secondPerson_confirm_correctCode_success() {
        givenConfirmers(confirmer("witness@x.com", DmsConfirmerStatus.ACTIVE));
        when(otpGenerator.generate()).thenReturn("482910");
        DigitalAsset asset = documentAsset(null);
        AssetVerifier.StartResult start = verifier.start(asset, AssetVerificationMethod.SECOND_PERSON_CONFIRM, ownerId);

        AssetVerifier.VerifyResult result = verifier.confirm(asset, AssetVerificationMethod.SECOND_PERSON_CONFIRM,
                Map.of("code", "482910"), ownerId, start.challengeHash());

        assertThat(result.success()).isTrue();
        assertThat(result.provider()).isEqualTo(VerificationProvider.REAL);
        assertThat(result.evidence()).containsEntry("check", "SECOND_PERSON_CODE");
        assertThat(result.evidence().values()).noneMatch(v -> v.contains("@"));
    }

    @Test
    @DisplayName("SECOND_PERSON_CONFIRM confirm: sai mã thì thất bại")
    void secondPerson_confirm_wrongCode_fails() {
        givenConfirmers(confirmer("witness@x.com", DmsConfirmerStatus.ACTIVE));
        when(otpGenerator.generate()).thenReturn("482910");
        DigitalAsset asset = documentAsset(null);
        AssetVerifier.StartResult start = verifier.start(asset, AssetVerificationMethod.SECOND_PERSON_CONFIRM, ownerId);

        AssetVerifier.VerifyResult result = verifier.confirm(asset, AssetVerificationMethod.SECOND_PERSON_CONFIRM,
                Map.of("code", "000000"), ownerId, start.challengeHash());

        assertThat(result.success()).isFalse();
    }

    @Test
    @DisplayName("SECOND_PERSON_CONFIRM confirm: thiếu mã hoặc chưa start (không có challengeHash) thì thất bại")
    void secondPerson_confirm_missingCodeOrChallenge_fails() {
        DigitalAsset asset = documentAsset(null);

        assertThat(verifier.confirm(asset, AssetVerificationMethod.SECOND_PERSON_CONFIRM,
                Map.of(), ownerId, "a".repeat(64)).success()).isFalse();
        assertThat(verifier.confirm(asset, AssetVerificationMethod.SECOND_PERSON_CONFIRM,
                Map.of("code", "482910"), ownerId, null).success()).isFalse();
    }
}
