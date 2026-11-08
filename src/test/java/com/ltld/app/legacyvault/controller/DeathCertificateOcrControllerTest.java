package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.security.SecurityConfig;
import com.ltld.app.legacyvault.dto.ocrdto.DeathCertificateOcrResponseDto;
import com.ltld.app.legacyvault.service.ocrservice.DeathCertificateOcrService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DeathCertificateOcrController.class)
@Import(SecurityConfig.class)
class DeathCertificateOcrControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DeathCertificateOcrService deathCertificateOcrService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void analyzeForExecutor_returns401_whenUnauthenticated() throws Exception {
        UUID assignmentId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "trich_luc_khai_tu.pdf",
                "application/pdf",
                "%PDF-1.4 test".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/executor-assignments/{assignmentId}/ocr-death-certificate", assignmentId)
                        .file(file))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(deathCertificateOcrService);
    }

    @Test
    void analyzeForExecutor_returnsOcrResult_forAuthenticatedExecutor() throws Exception {
        UUID assignmentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "trich_luc_khai_tu.pdf",
                "application/pdf",
                "%PDF-1.4 test".getBytes(StandardCharsets.UTF_8)
        );

        DeathCertificateOcrResponseDto dto = DeathCertificateOcrResponseDto.builder()
                .engineSource("GEMINI_VISION_AI")
                .documentCategory("VALID_DEATH_CERTIFICATE")
                .deceasedFullName("TRẦN LONG VÂN")
                .expectedOwnerName("Trần Long Vân")
                .ownerNameMatched(true)
                .hasOfficialSeal(true)
                .confidenceScore(93)
                .warnings(List.of())
                .build();

        when(deathCertificateOcrService.analyzeForExecutor(eq(assignmentId), eq(userId.toString()), any()))
                .thenReturn(dto);

        mockMvc.perform(multipart("/api/executor-assignments/{assignmentId}/ocr-death-certificate", assignmentId)
                        .file(file)
                        .with(jwt().jwt(j -> j.subject(userId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.engineSource").value("GEMINI_VISION_AI"))
                .andExpect(jsonPath("$.data.ownerNameMatched").value(true));
    }

    @Test
    void analyzeForVerifier_returns403_forExecutorRole() throws Exception {
        UUID requestId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/api/verifier/requests/{id}/document/ocr", requestId)
                        .with(jwt()
                                .jwt(j -> j.subject(userId.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_EXECUTOR"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(deathCertificateOcrService);
    }

    @Test
    void analyzeForVerifier_returnsOcrResult_forAuthenticatedVerifierJwt() throws Exception {
        UUID requestId = UUID.randomUUID();
        UUID verifierId = UUID.randomUUID();

        DeathCertificateOcrResponseDto dto = DeathCertificateOcrResponseDto.builder()
                .engineSource("UNAVAILABLE")
                .unavailableMessage("Chưa phân tích được bằng AI, vui lòng tự kiểm tra")
                .expectedOwnerName("Trần Long Vân")
                .ownerNameMatched(false)
                .hasOfficialSeal(false)
                .warnings(List.of("Chưa phân tích được bằng AI, vui lòng tự kiểm tra"))
                .build();

        when(deathCertificateOcrService.analyzeForVerifier(eq(requestId), eq(verifierId)))
                .thenReturn(dto);

        mockMvc.perform(post("/api/verifier/requests/{id}/document/ocr", requestId)
                        .with(jwt()
                                .jwt(j -> j.subject(verifierId.toString()))
                                .authorities(
                                        new SimpleGrantedAuthority("ROLE_LEGAL_VERIFIER"),
                                        new SimpleGrantedAuthority("LEGAL_REVIEW")
                                )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.engineSource").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.data.unavailableMessage").value("Chưa phân tích được bằng AI, vui lòng tự kiểm tra"));
    }

    @Test
    void analyzeForVerifier_returns401_whenJwtSubjectIsNotUuid() throws Exception {
        UUID requestId = UUID.randomUUID();

        mockMvc.perform(post("/api/verifier/requests/{id}/document/ocr", requestId)
                        .with(jwt()
                                .jwt(j -> j.subject("not-a-valid-uuid"))
                                .authorities(
                                        new SimpleGrantedAuthority("ROLE_LEGAL_VERIFIER"),
                                        new SimpleGrantedAuthority("LEGAL_REVIEW")
                                )))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(deathCertificateOcrService);
    }
}
