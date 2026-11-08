package com.ltld.app.legacyvault.service.assetverificationservice;

import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.entity.LegalDocument;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.AssetVerificationMethod;
import com.ltld.app.legacyvault.enums.VerificationProvider;
import com.ltld.app.legacyvault.exception.VaultException;
import com.ltld.app.legacyvault.repository.LegalDocumentRepository;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import com.ltld.app.legacyvault.service.fileservice.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentAssetVerifier – C.3.7 xác thực tài sản DOCUMENT (FILE_HASH)")
class DocumentAssetVerifierTest {

    @Mock private LegalDocumentRepository legalDocumentRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private CryptoService cryptoService;

    private DocumentAssetVerifier verifier;
    private UUID ownerId;
    private UUID vaultId;
    private Vault vault;

    @BeforeEach
    void setUp() {
        verifier = new DocumentAssetVerifier(legalDocumentRepository, fileStorageService, cryptoService);
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
    @DisplayName("methods(): chỉ có FILE_HASH và type() là DOCUMENT")
    void methodsAndType() {
        assertThat(verifier.type()).isEqualTo(AssetType.DOCUMENT);
        assertThat(verifier.methods()).containsExactly(AssetVerificationMethod.FILE_HASH);
    }

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

    @Test
    @DisplayName("Cách chưa hỗ trợ (SECOND_PERSON_CONFIRM) bị từ chối ở start và confirm")
    void unsupportedMethod_throws() {
        DigitalAsset asset = documentAsset(null);

        assertThatThrownBy(() -> verifier.start(asset, AssetVerificationMethod.SECOND_PERSON_CONFIRM, ownerId))
                .isInstanceOf(VaultException.class);
        assertThatThrownBy(() -> verifier.confirm(asset, AssetVerificationMethod.SECOND_PERSON_CONFIRM,
                Map.of(), ownerId, null)).isInstanceOf(VaultException.class);
    }
}
