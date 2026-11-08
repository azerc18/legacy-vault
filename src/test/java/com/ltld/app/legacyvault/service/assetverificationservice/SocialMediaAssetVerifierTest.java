package com.ltld.app.legacyvault.service.assetverificationservice;

import com.ltld.app.legacyvault.entity.DigitalAsset;
import com.ltld.app.legacyvault.entity.LegalDocument;
import com.ltld.app.legacyvault.entity.Vault;
import com.ltld.app.legacyvault.enums.AssetType;
import com.ltld.app.legacyvault.enums.AssetVerificationMethod;
import com.ltld.app.legacyvault.enums.VerificationProvider;
import com.ltld.app.legacyvault.repository.LegalDocumentRepository;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import com.ltld.app.legacyvault.service.fileservice.FileStorageService;
import com.ltld.app.legacyvault.utility.OtpGenerator;
import com.ltld.app.legacyvault.utility.OtpHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SocialMediaAssetVerifier – C.3.6 xác thực tài sản SOCIAL_MEDIA")
class SocialMediaAssetVerifierTest {

    @Mock private OtpGenerator otpGenerator;
    @Mock private LegalDocumentRepository legalDocumentRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private CryptoService cryptoService;

    private SocialMediaAssetVerifier verifier;
    private UUID ownerId;
    private UUID vaultId;
    private UUID assetId;
    private Vault vault;

    @BeforeEach
    void setUp() {
        verifier = new SocialMediaAssetVerifier(otpGenerator, new OtpHasher(), legalDocumentRepository,
                fileStorageService, cryptoService);
        ownerId = UUID.randomUUID();
        vaultId = UUID.randomUUID();
        assetId = UUID.randomUUID();
        vault = Vault.builder().id(vaultId).build();
    }

    private DigitalAsset socialAsset(String platform, String handle) {
        return DigitalAsset.builder()
                .id(assetId)
                .vault(vault)
                .assetType(AssetType.SOCIAL_MEDIA)
                .assetName(platform + " Account")
                .publicFieldsJson("{\"platform\":\"" + platform + "\",\"usernameOrHandle\":\"" + handle + "\"}")
                .build();
    }

    @Test
    @DisplayName("BIO_CHALLENGE_MOCK: tạo mã LV-XXXXXX và trích xuất được mã từ đoạn tiểu sử (bio) người dùng")
    void bioChallengeMock_startAndConfirmFromBioText_success() {
        DigitalAsset asset = socialAsset("Facebook", "fb.com/tranlongvan");
        when(otpGenerator.generate()).thenReturn("778899");

        AssetVerifier.StartResult start = verifier.start(asset, AssetVerificationMethod.BIO_CHALLENGE_MOCK, ownerId);

        assertThat(start.provider()).isEqualTo(VerificationProvider.SANDBOX);
        assertThat(start.clientData()).containsEntry("challengeCode", "LV-778899");

        AssetVerifier.VerifyResult confirm = verifier.confirm(
                asset,
                AssetVerificationMethod.BIO_CHALLENGE_MOCK,
                Map.of("bioText", "Software Engineer | LegacyVault verification: lv-778899"),
                ownerId,
                start.challengeHash()
        );

        assertThat(confirm.success()).isTrue();
        assertThat(confirm.provider()).isEqualTo(VerificationProvider.SANDBOX);
        assertThat(confirm.evidence()).containsEntry("challengeCode", "LV-778899");
    }

    @Test
    @DisplayName("SCREENSHOT_UPLOAD: ghi nhận ảnh trong kho tài liệu của vault, server tự băm nội dung tệp, trả SANDBOX")
    void screenshotUpload_withVaultDocumentId_success() throws Exception {
        DigitalAsset asset = socialAsset("Instagram", "@longvan");
        UUID docId = UUID.randomUUID();
        LegalDocument doc = LegalDocument.builder()
                .id(docId)
                .vault(vault)
                .fileName("instagram_profile.png")
                .fileUrlEncrypted("enc://bucket/ig.png")
                .build();
        when(legalDocumentRepository.findByIdAndVaultId(docId, vaultId)).thenReturn(Optional.of(doc));
        byte[] cipher = new byte[]{9, 9};
        byte[] plain = "png-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(fileStorageService.readFile("enc://bucket/ig.png")).thenReturn(cipher);
        when(cryptoService.decryptBytes(cipher)).thenReturn(plain);

        AssetVerifier.VerifyResult result = verifier.confirm(
                asset,
                AssetVerificationMethod.SCREENSHOT_UPLOAD,
                Map.of("documentId", docId.toString()),
                ownerId,
                null
        );

        assertThat(result.success()).isTrue();
        assertThat(result.provider()).isEqualTo(VerificationProvider.SANDBOX);
        assertThat(result.evidence())
                .containsEntry("documentId", docId.toString())
                .containsEntry("fileName", "instagram_profile.png")
                .containsEntry("sha256", java.util.HexFormat.of().formatHex(
                        java.security.MessageDigest.getInstance("SHA-256").digest(plain)));
    }

    @Test
    @DisplayName("SCREENSHOT_UPLOAD: chỉ gửi chuỗi sha256 (không có tài liệu thật) thì thất bại")
    void screenshotUpload_rawHashOnly_fails() {
        DigitalAsset asset = socialAsset("TikTok", "@longvan");

        AssetVerifier.VerifyResult result = verifier.confirm(asset, AssetVerificationMethod.SCREENSHOT_UPLOAD,
                Map.of("sha256", "a".repeat(64)), ownerId, null);

        assertThat(result.success()).isFalse();
    }

    @Test
    @DisplayName("SCREENSHOT_UPLOAD: tài liệu ở vault khác thì thất bại")
    void screenshotUpload_documentFromOtherVault_fails() {
        DigitalAsset asset = socialAsset("Instagram", "@longvan");
        UUID docId = UUID.randomUUID();
        when(legalDocumentRepository.findByIdAndVaultId(docId, vaultId)).thenReturn(Optional.empty());

        AssetVerifier.VerifyResult result = verifier.confirm(asset, AssetVerificationMethod.SCREENSHOT_UPLOAD,
                Map.of("documentId", docId.toString()), ownerId, null);

        assertThat(result.success()).isFalse();
    }

    @Test
    @DisplayName("SCREENSHOT_UPLOAD: thiếu cả documentId lẫn fileHash trả success=false")
    void screenshotUpload_missingEvidence_fails() {
        DigitalAsset asset = socialAsset("X", "@longvan");

        AssetVerifier.VerifyResult result = verifier.confirm(
                asset, AssetVerificationMethod.SCREENSHOT_UPLOAD, Map.of(), ownerId, null);

        assertThat(result.success()).isFalse();
        assertThat(result.provider()).isEqualTo(VerificationProvider.SANDBOX);
    }
}
