package com.ltld.app.legacyvault.service.ocrservice;

import com.ltld.app.legacyvault.dto.ocrdto.DeathCertificateOcrResponseDto;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.LegalVerificationRequest;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
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
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
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
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private PlatformTransactionManager transactionManager;

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
                new ObjectMapper(),
                auditLogService,
                transactionManager
        );
        ReflectionTestUtils.setField(ocrService, "aiProvider", "mock");
        ReflectionTestUtils.setField(ocrService, "apiKey", "");
        ReflectionTestUtils.setField(ocrService, "model", "gemini-3.6-flash");
        ReflectionTestUtils.setField(ocrService, "backupModel", "gemini-3.5-flash");
        ReflectionTestUtils.setField(ocrService, "demoScenariosEnabled", false);
    }

    @Test
    void analyzeForExecutor_returnsUnavailableWithoutFakeData_whenAiDisabledAndDemoFlagFalse() {
        UUID assignmentId = UUID.randomUUID();
        UUID executorId = UUID.randomUUID();
        User owner = new User();
        owner.setFullName("Trần Long Vân");
        User executor = new User();
        executor.setId(executorId);

        ExecutorAssignment assignment = new ExecutorAssignment();
        assignment.setId(assignmentId);
        assignment.setOwner(owner);
        assignment.setExecutorUser(executor);

        when(executorAccessGuard.requireActiveAssignment(assignmentId, "executor@example.com"))
                .thenReturn(assignment);

        byte[] pdfBytes = "%PDF-1.4 sample death certificate".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file", "giay_chung_tu_sai_ten.pdf", "application/pdf", pdfBytes);

        DeathCertificateOcrResponseDto result =
                ocrService.analyzeForExecutor(assignmentId, "executor@example.com", file);

        assertThat(result).isNotNull();
        assertThat(result.getEngineSource()).isEqualTo("UNAVAILABLE");
        assertThat(result.getUnavailableMessage()).contains("Chưa phân tích được bằng AI");
        assertThat(result.isOwnerNameMatched()).isFalse();
        assertThat(result.isHasOfficialSeal()).isFalse();
        assertThat(result.getDeceasedFullName()).isNull();
        assertThat(result.getCertificateNumber()).isNull();
        assertThat(result.getIdNumber()).isNull();
        assertThat(result.getConfidenceScore()).isNull();
        assertThat(result.getImageQualityScore()).isNull();
        assertThat(result.getSummaryNote()).isNull();

        verify(auditLogService).log(
                eq(AuditAction.LEGAL_DOCUMENT_OCR_ANALYZED),
                eq(AuditResult.FAILURE),
                eq(executorId),
                eq("executor@example.com"),
                eq("ExecutorAssignment"),
                eq(assignmentId.toString()),
                eq("engine=UNAVAILABLE")
        );
    }

    @Test
    void parseGeminiJson_leavesMissingFieldsNull_andNeverFakesOwnerMatchOrSeal() throws Exception {
        String partialGeminiJson = """
                {
                  "documentCategory": "SOMETHING_INVALID",
                  "deceasedFullName": null,
                  "certificateNumber": "",
                  "idNumber": "unknown"
                }
                """;

        DeathCertificateOcrResponseDto result =
                ocrService.parseGeminiJson(partialGeminiJson, "Trần Long Vân");

        assertThat(result.getEngineSource()).isEqualTo("GEMINI_VISION_AI");
        assertThat(result.getDocumentCategory()).isEqualTo("UNRELATED_DOCUMENT");
        assertThat(result.getDeceasedFullName()).isNull();
        assertThat(result.isOwnerNameMatched()).isFalse();
        assertThat(result.isHasOfficialSeal()).isFalse();
        assertThat(result.getCertificateNumber()).isNull();
        assertThat(result.getIdNumber()).isNull();
        assertThat(result.getIssuingAuthority()).isNull();
        assertThat(result.getImageQualityScore()).isNull();
        assertThat(result.getDeathProofRelevancePercent()).isNull();
        assertThat(result.getSummaryNote()).isNull();
    }

    @Test
    void parseGeminiJson_parsesValidDeathCertificate_andDetectsInternalCccdBirthYearMismatch() throws Exception {
        String validJsonWithForgedBirthYear = """
                {
                  "documentCategory": "VALID_DEATH_CERTIFICATE",
                  "documentType": "Trích lục khai tử (Bản sao)",
                  "detectedHeaderTitle": "TRÍCH LỤC KHAI TỬ (BẢN SAO)",
                  "imageQualityScore": 94,
                  "deathProofRelevancePercent": 95,
                  "certificateNumber": "319/TLKT-BS",
                  "deceasedFullName": "TRẦN LONG VÂN",
                  "dateOfBirth": "18/04/1975",
                  "gender": "Nam",
                  "idNumber": "079065004812",
                  "dateOfDeath": "02/11/2026",
                  "issuingAuthority": "UBND Phường Bến Nghé",
                  "hasOfficialSeal": true
                }
                """;

        DeathCertificateOcrResponseDto result =
                ocrService.parseGeminiJson(validJsonWithForgedBirthYear, "Trần Long Vân");

        assertThat(result.getEngineSource()).isEqualTo("GEMINI_VISION_AI");
        assertThat(result.isOwnerNameMatched()).isTrue();
        assertThat(result.isHasOfficialSeal()).isTrue();
        assertThat(result.getSuggestedRejectionCode()).isEqualTo("SUSPECTED_FORGERY");
        assertThat(result.getDeathProofRelevancePercent()).isLessThanOrEqualTo(38);
        assertThat(result.getWarnings()).anyMatch(w -> w.contains("MÂU THUẪN NỘI TẠI"));
        assertThat(result.getSummaryNote()).doesNotContain("100%");
    }

    @Test
    void isNameMatching_requiresExactNormalizedFullName_andRejectsPartialSubstring() {
        assertThat(DeathCertificateOcrServiceImpl.isNameMatching("TRẦN LONG VÂN", "Trần Long Vân")).isTrue();
        assertThat(DeathCertificateOcrServiceImpl.isNameMatching("  Tran   Long Van ", "Trần Long Vân")).isTrue();
        assertThat(DeathCertificateOcrServiceImpl.isNameMatching("Văn An", "Trần Văn An")).isFalse();
        assertThat(DeathCertificateOcrServiceImpl.isNameMatching("Trần Văn An", "Văn An")).isFalse();
        assertThat(DeathCertificateOcrServiceImpl.isNameMatching("Long Vân", "Trần Long Vân")).isFalse();
    }

    @Test
    void analyzeForExecutor_usesDemoSimulationOnlyWhenDemoFlagExplicitlyEnabled() {
        ReflectionTestUtils.setField(ocrService, "demoScenariosEnabled", true);

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

        assertThat(blurryResult.getEngineSource()).isEqualTo("DEMO_SIMULATION");
        assertThat(blurryResult.getImageQualityScore()).isLessThan(60);
        assertThat(blurryResult.getSuggestedRejectionCode()).isEqualTo("DOCUMENT_UNREADABLE");
    }

    @Test
    void analyzeForVerifier_decryptsAndAuditsWithoutExposingEncryptedPathAsFilename() throws Exception {
        UUID requestId = UUID.randomUUID();
        UUID verifierId = UUID.randomUUID();
        User owner = new User();
        owner.setFullName("Nguyễn Thị Mai");

        LegalVerificationRequest request = new LegalVerificationRequest();
        request.setId(requestId);
        request.setOwner(owner);
        request.setDeathCertificateFileUrlEncrypted("enc/giay_chung_tu_sai_ten.bin");

        byte[] encrypted = new byte[]{1, 2, 3, 4};
        byte[] decrypted = "%PDF-1.4 decrypted cert".getBytes(StandardCharsets.UTF_8);

        when(verifierAccessGuard.requireReadable(requestId, verifierId)).thenReturn(request);
        when(fileStorageService.readFile("enc/giay_chung_tu_sai_ten.bin")).thenReturn(encrypted);
        when(cryptoService.decryptBytes(encrypted)).thenReturn(decrypted);

        DeathCertificateOcrResponseDto result = ocrService.analyzeForVerifier(requestId, verifierId);

        assertThat(result.getEngineSource()).isEqualTo("UNAVAILABLE");
        assertThat(result.getExpectedOwnerName()).isEqualTo("Nguyễn Thị Mai");
        verify(auditLogService).log(
                eq(AuditAction.LEGAL_DOCUMENT_OCR_ANALYZED),
                eq(AuditResult.FAILURE),
                eq(verifierId),
                isNull(),
                eq("LegalVerificationRequest"),
                eq(requestId.toString()),
                eq("engine=UNAVAILABLE")
        );
    }
    @Test
    void analyzeBytes_fallsBackToBackupModel_whenPrimaryFails() {
        ReflectionTestUtils.setField(ocrService, "aiProvider", "gemini");
        ReflectionTestUtils.setField(ocrService, "apiKey", "dummy-key");
        ReflectionTestUtils.setField(ocrService, "model", "failing-primary-model");
        ReflectionTestUtils.setField(ocrService, "backupModel", "failing-backup-model");

        byte[] pdfBytes = "%PDF-1.4 test".getBytes(StandardCharsets.UTF_8);
        DeathCertificateOcrResponseDto result = ocrService.analyzeBytes(pdfBytes, "cert.pdf", "Trần Long Vân");

        assertThat(result).isNotNull();
        assertThat(result.getEngineSource()).isEqualTo("UNAVAILABLE");
        assertThat(result.getUnavailableMessage()).contains("Chưa phân tích được bằng AI");
    }
}
