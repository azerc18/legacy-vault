package com.ltld.app.legacyvault.service.legalverificationservice;

import com.ltld.app.legacyvault.dto.legalverificationdto.VerifierSignatureDto;
import com.ltld.app.legacyvault.entity.DigitalSignature;
import com.ltld.app.legacyvault.entity.LegalVerificationRequest;
import com.ltld.app.legacyvault.entity.SigningKey;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.SignatureMethod;
import com.ltld.app.legacyvault.enums.SignatureSealStatus;
import com.ltld.app.legacyvault.exception.StorageUnavailableException;
import com.ltld.app.legacyvault.repository.SigningKeyRepository;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import com.ltld.app.legacyvault.service.fileservice.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * V3: ký thật bằng ECDSA (không mock phần mật mã), chỉ mock lưu trữ file và mã hóa khóa riêng.
 */
@ExtendWith(MockitoExtension.class)
class VerifierSignatureSealServiceTest {

    @Mock private SigningKeyRepository keyRepository;
    @Mock private CryptoService cryptoService;
    @Mock private FileStorageService fileStorageService;
    @Mock private PlatformTransactionManager transactionManager;

    private SigningKeyService keyService;
    private VerifierSignatureSealService sealService;
    private SigningKey key;
    private LegalVerificationRequest request;
    private User verifier;
    private final byte[] document = "%PDF-1.7 death certificate".getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    void setUp() throws Exception {
        // "Mã hóa" khóa riêng bằng identity để test chạy ký/kiểm thật
        lenient().when(cryptoService.encrypt(anyString())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(cryptoService.decrypt(anyString())).thenAnswer(inv -> inv.getArgument(0));
        keyService = new SigningKeyService(keyRepository, cryptoService, transactionManager);
        sealService = new VerifierSignatureSealService(keyService, fileStorageService, cryptoService);
        key = keyService.generate("lv-sign-v1");
        lenient().when(keyRepository.findFirstByActiveTrueOrderByCreatedAtDesc()).thenReturn(java.util.Optional.of(key));

        verifier = User.builder().id(UUID.randomUUID()).fullName("Verifier A").email("v@test.local").build();
        request = LegalVerificationRequest.builder()
                .id(UUID.randomUUID())
                .owner(User.builder().id(UUID.randomUUID()).build())
                .executor(User.builder().id(UUID.randomUUID()).build())
                .deathCertificateFileUrlEncrypted("enc://cert.pdf")
                .build();
        lenient().when(fileStorageService.readFile("enc://cert.pdf")).thenReturn(document);
        lenient().when(cryptoService.decryptBytes(document)).thenReturn(document);
    }

    private DigitalSignature sealed(List<UUID> vaults) {
        DigitalSignature s = DigitalSignature.builder()
                .id(UUID.randomUUID()).request(request).verifier(verifier)
                .signatureMethod(SignatureMethod.MOCK_OTP).signatureHash("old").build();
        sealService.seal(s, request, verifier.getEmail(), vaults);
        return s;
    }

    @Test
    void seal_fillsAllFields_andPayloadCarriesDocumentHashAndSortedVaults() {
        UUID a = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID b = UUID.fromString("00000000-0000-0000-0000-000000000001");
        DigitalSignature s = sealed(List.of(a, b));

        String docSha = SigningKeyService.sha256Hex(document);
        assertThat(s.getDocumentSha256()).isEqualTo(docSha);
        assertThat(s.getSignatureAlgorithm()).isEqualTo("SHA256withECDSA");
        assertThat(s.getSigningKey()).isSameAs(key);
        assertThat(s.getSignatureValue()).isNotBlank();
        // mã băm cũ giờ là SHA-256 của payload, không còn là "old"
        assertThat(s.getSignatureHash())
                .isEqualTo(SigningKeyService.sha256Hex(s.getSignedPayload().getBytes(StandardCharsets.UTF_8)));

        Map<String, String> f = VerifierSignatureSealService.parsePayload(s.getSignedPayload());
        assertThat(f).containsEntry("request_id", request.getId().toString())
                .containsEntry("verifier_id", verifier.getId().toString())
                .containsEntry("document_sha256", docSha)
                .containsEntry("method", "MOCK_OTP")
                .containsEntry("vault_ids", b + "," + a)
                .containsEntry("key_ref", "lv-sign-v1");
    }

    @Test
    void check_untouched_isValid() {
        VerifierSignatureDto dto = sealService.check(sealed(List.of(UUID.randomUUID())));

        assertThat(dto.getStatus()).isEqualTo(SignatureSealStatus.VALID);
        assertThat(dto.getSignatureValid()).isTrue();
        assertThat(dto.getPayloadMatches()).isTrue();
        assertThat(dto.getDocumentMatches()).isTrue();
        assertThat(dto.getPublicKeyPem()).startsWith("-----BEGIN PUBLIC KEY-----");
        assertThat(dto.getKeyFingerprint()).hasSize(64);
    }

    @Test
    void check_payloadTampered_isInvalid() {
        DigitalSignature s = sealed(List.of(UUID.randomUUID()));
        s.setSignedPayload(s.getSignedPayload().replace("MOCK_OTP", "MOCK_PIN"));

        VerifierSignatureDto dto = sealService.check(s);

        assertThat(dto.getStatus()).isEqualTo(SignatureSealStatus.INVALID);
        assertThat(dto.getSignatureValid()).isFalse();
    }

    @Test
    void check_documentReplacedAfterSigning_isInvalid() throws Exception {
        DigitalSignature s = sealed(List.of(UUID.randomUUID()));
        byte[] other = "%PDF-1.7 another file".getBytes(StandardCharsets.UTF_8);
        when(fileStorageService.readFile("enc://cert.pdf")).thenReturn(other);
        when(cryptoService.decryptBytes(other)).thenReturn(other);

        VerifierSignatureDto dto = sealService.check(s);

        assertThat(dto.getStatus()).isEqualTo(SignatureSealStatus.INVALID);
        assertThat(dto.getSignatureValid()).isTrue();
        assertThat(dto.getDocumentMatches()).isFalse();
    }

    @Test
    void check_signatureCopiedToAnotherRequest_isInvalid() {
        DigitalSignature s = sealed(List.of(UUID.randomUUID()));
        s.setRequest(LegalVerificationRequest.builder().id(UUID.randomUUID())
                .deathCertificateFileUrlEncrypted("enc://cert.pdf").build());

        VerifierSignatureDto dto = sealService.check(s);

        assertThat(dto.getStatus()).isEqualTo(SignatureSealStatus.INVALID);
        assertThat(dto.getPayloadMatches()).isFalse();
    }

    @Test
    void check_storageDown_isDocumentUnavailable() throws Exception {
        DigitalSignature s = sealed(List.of(UUID.randomUUID()));
        when(fileStorageService.readFile("enc://cert.pdf"))
                .thenThrow(new StorageUnavailableException("down", null));

        VerifierSignatureDto dto = sealService.check(s);

        assertThat(dto.getStatus()).isEqualTo(SignatureSealStatus.DOCUMENT_UNAVAILABLE);
        assertThat(dto.getDocumentMatches()).isNull();
    }

    @Test
    void check_oldSignatureWithoutSeal_isLegacy() {
        DigitalSignature old = DigitalSignature.builder().id(UUID.randomUUID()).request(request)
                .verifier(verifier).signatureMethod(SignatureMethod.MOCK_OTP).signatureHash("abc").build();

        VerifierSignatureDto dto = sealService.check(old);

        assertThat(dto.getStatus()).isEqualTo(SignatureSealStatus.LEGACY);
        assertThat(dto.getSignatureHash()).isEqualTo("abc");
        assertThat(dto.getSignatureValid()).isNull();
    }

    @Test
    void seal_storageDown_throws_soSigningRollsBack() throws Exception {
        when(fileStorageService.readFile("enc://cert.pdf"))
                .thenThrow(new StorageUnavailableException("down", null));
        DigitalSignature s = DigitalSignature.builder().request(request).verifier(verifier)
                .signatureMethod(SignatureMethod.MOCK_OTP).build();

        assertThatThrownBy(() -> sealService.seal(s, request, "v@test.local", List.of()))
                .isInstanceOf(StorageUnavailableException.class);
    }

    @Test
    void verify_otherKey_isFalse() {
        SigningKey other = keyService.generate("lv-sign-x");
        DigitalSignature s = sealed(List.of());

        assertThat(keyService.verify(other.getPublicKeyPem(), s.getSignedPayload(), s.getSignatureValue())).isFalse();
        assertThat(keyService.verify(key.getPublicKeyPem(), s.getSignedPayload(), "not-base64!!")).isFalse();
    }
}
