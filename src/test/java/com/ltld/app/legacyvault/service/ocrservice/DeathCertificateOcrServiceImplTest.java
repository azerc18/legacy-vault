package com.ltld.app.legacyvault.service.ocrservice;

import com.ltld.app.legacyvault.dto.ocrdto.DeathCertificateOcrResponseDto;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.LegalVerificationRequest;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.service.cryptoservice.CryptoService;
import com.ltld.app.legacyvault.service.executorservice.ExecutorAccessGuard;
import com.ltld.app.legacyvault.service.fileservice.FileStorageService;
import com.ltld.app.legacyvault.service.fileservice.UploadedDocumentValidator;
import com.ltld.app.legacyvault.service.legalverificationservice.VerifierAccessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeathCertificateOcrServiceImplTest {

    @Mock
    private ExecutorAccessGuard executorAccessGuard;
    @Mock
    private VerifierAccessGuard verifierAccessGuard;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private CryptoService cryptoService;

    private DeathCertificateOcrServiceImpl ocrService;

    @BeforeEach
    void setUp() {
        UploadedDocumentValidator validator = new UploadedDocumentValidator();
        ocrService = new DeathCertificateOcrServiceImpl(
                executorAccessGuard,
                verifierAccessGuard,
                validator,
                fileStorageService,
                cryptoService,
                new ObjectMapper()
        );
        ReflectionTestUtils.setField(ocrService, "aiProvider", "mock");
        ReflectionTestUtils.setField(ocrService, "apiKey", "");
        ReflectionTestUtils.setField(ocrService, "model", "gemini-3.6-flash");
    }

    @Test
    void analyzeForExecutor_returnsMatchedFallbackResult_forValidPdf() {
        UUID assignmentId = UUID.randomUUID();
        User owner = new User();
        owner.setFullName("Trần Long Vân");
        ExecutorAssignment assignment = new ExecutorAssignment();
        assignment.setId(assignmentId);
        assignment.setOwner(owner);

        when(executorAccessGuard.requireActiveAssignment(assignmentId, "executor@example.com"))
                .thenReturn(assignment);

        byte[] pdfBytes = "%PDF-1.4 sample death certificate".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file", "trich_luc_khai_tu.pdf", "application/pdf", pdfBytes);

        DeathCertificateOcrResponseDto result =
                ocrService.analyzeForExecutor(assignmentId, "executor@example.com", file);

        assertThat(result).isNotNull();
        assertThat(result.isOwnerNameMatched()).isTrue();
        assertThat(result.getExpectedOwnerName()).isEqualTo("Trần Long Vân");
        assertThat(result.getDeceasedFullName()).isEqualToIgnoringCase("TRẦN LONG VÂN");
        assertThat(result.isHasOfficialSeal()).isTrue();
        assertThat(result.getConfidenceScore()).isGreaterThanOrEqualTo(95);
        assertThat(result.getImageQualityScore()).isGreaterThanOrEqualTo(90);
        assertThat(result.getDetectedHeaderTitle()).contains("TRÍCH LỤC KHAI TỬ");
        assertThat(result.getDeathProofRelevancePercent()).isGreaterThanOrEqualTo(95);
        assertThat(result.getExecutorSuggestion()).contains("Tài liệu rõ nét");
        assertThat(result.getVerifierSuggestion()).contains("Gợi ý cho Verifier");
        assertThat(result.getSummaryNote()).contains("Khớp với Chủ két");
        assertThat(result.getWarnings()).isEmpty();
    }

    @Test
    void analyzeForExecutor_detectsNameMismatch_whenMismatchFilenameProvided() {
        UUID assignmentId = UUID.randomUUID();
        User owner = new User();
        owner.setFullName("Trần Long Vân");
        ExecutorAssignment assignment = new ExecutorAssignment();
        assignment.setId(assignmentId);
        assignment.setOwner(owner);

        when(executorAccessGuard.requireActiveAssignment(assignmentId, "executor@example.com"))
                .thenReturn(assignment);

        byte[] pdfBytes = "%PDF-1.4 mismatch certificate".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file", "giay_chung_tu_sai_ten.pdf", "application/pdf", pdfBytes);

        DeathCertificateOcrResponseDto result =
                ocrService.analyzeForExecutor(assignmentId, "executor@example.com", file);

        assertThat(result.isOwnerNameMatched()).isFalse();
        assertThat(result.getDeathProofRelevancePercent()).isLessThanOrEqualTo(20);
        assertThat(result.getWarnings()).isNotEmpty();
        assertThat(result.getSummaryNote()).contains("Không khớp với tên Chủ két");
    }

    @Test
    void analyzeForExecutor_evaluatesBlurryAndRelatedDocuments_withAdvisorySuggestions() {
        UUID assignmentId = UUID.randomUUID();
        User owner = new User();
        owner.setFullName("Trần Long Vân");
        ExecutorAssignment assignment = new ExecutorAssignment();
        assignment.setId(assignmentId);
        assignment.setOwner(owner);

        when(executorAccessGuard.requireActiveAssignment(assignmentId, "executor@example.com"))
                .thenReturn(assignment);

        byte[] pdfBytes = "%PDF-1.4 sample".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile blurryFile = new MockMultipartFile(
                "file", "giay_chung_tu_mo_nhoe.pdf", "application/pdf", pdfBytes);

        DeathCertificateOcrResponseDto blurryResult =
                ocrService.analyzeForExecutor(assignmentId, "executor@example.com", blurryFile);

        assertThat(blurryResult.getImageQualityScore()).isLessThan(60);
        assertThat(blurryResult.getSuggestedRejectionCode()).isEqualTo("DOCUMENT_UNREADABLE");

        MockMultipartFile relatedFile = new MockMultipartFile(
                "file", "giay_bao_tu_benh_vien.pdf", "application/pdf", pdfBytes);

        DeathCertificateOcrResponseDto relatedResult =
                ocrService.analyzeForExecutor(assignmentId, "executor@example.com", relatedFile);

        assertThat(relatedResult.getDocumentCategory()).isEqualTo("RELATED_SUPPORTING_DOCUMENT");
        assertThat(relatedResult.getDeathProofRelevancePercent()).isBetween(65, 85);

        MockMultipartFile forgedSmallDetailFile = new MockMultipartFile(
                "file", "giay_chung_tu_gia_mao_nam_sinh.pdf", "application/pdf", pdfBytes);

        DeathCertificateOcrResponseDto forgedResult =
                ocrService.analyzeForExecutor(assignmentId, "executor@example.com", forgedSmallDetailFile);

        assertThat(forgedResult.isOwnerNameMatched()).isTrue();
        assertThat(forgedResult.getImageQualityScore()).isGreaterThanOrEqualTo(90);
        assertThat(forgedResult.getSuggestedRejectionCode()).isEqualTo("SUSPECTED_FORGERY");
        assertThat(forgedResult.getDeathProofRelevancePercent()).isLessThanOrEqualTo(40);
        assertThat(forgedResult.getWarnings()).anyMatch(w -> w.contains("MÂU THUẪN NỘI TẠI"));
    }

    @Test
    void analyzeForVerifier_decryptsAndAnalyzesStoredDocument() throws Exception {
        UUID requestId = UUID.randomUUID();
        UUID verifierId = UUID.randomUUID();
        User owner = new User();
        owner.setFullName("Nguyễn Thị Mai");

        LegalVerificationRequest request = new LegalVerificationRequest();
        request.setId(requestId);
        request.setOwner(owner);
        request.setDeathCertificateFileUrlEncrypted("enc/cert-01.bin");

        byte[] encrypted = new byte[]{1, 2, 3, 4};
        byte[] decrypted = "%PDF-1.4 decrypted cert".getBytes(StandardCharsets.UTF_8);

        when(verifierAccessGuard.requireReadable(requestId, verifierId)).thenReturn(request);
        when(fileStorageService.readFile("enc/cert-01.bin")).thenReturn(encrypted);
        when(cryptoService.decryptBytes(encrypted)).thenReturn(decrypted);

        DeathCertificateOcrResponseDto result = ocrService.analyzeForVerifier(requestId, verifierId);

        assertThat(result.isOwnerNameMatched()).isTrue();
        assertThat(result.getExpectedOwnerName()).isEqualTo("Nguyễn Thị Mai");
        assertThat(result.getCertificateNumber()).endsWith("/TLKT-BS");
    }
}
