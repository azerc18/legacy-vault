package com.ltld.app.legacyvault.controller.verificationmessage;

import com.ltld.app.legacyvault.controller.LegalVerificationController;
import com.ltld.app.legacyvault.dto.legalverificationdto.PdfSignatureReportDto;
import com.ltld.app.legacyvault.exception.VerificationRequestNotFoundException;
import com.ltld.app.legacyvault.exception.VerificationStepRequiredException;
import com.ltld.app.legacyvault.security.SecurityConfig;
import com.ltld.app.legacyvault.service.legalverificationservice.LegalVerificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Review #295 m3: security test tầng HTTP cho POST /api/verifier/requests/{id}/document/signatures (V2). */
@WebMvcTest(LegalVerificationController.class)
@Import(SecurityConfig.class)
class DocumentSignaturesControllerSecurityTest {

    private static final String URL = "/api/verifier/requests/{id}/document/signatures";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private LegalVerificationService legalVerificationService;
    @MockitoBean private JwtDecoder jwtDecoder;

    private final UUID requestId = UUID.randomUUID();
    private final UUID verifierId = UUID.randomUUID();

    private static RequestPostProcessor token(UUID userId, String... authorities) {
        SimpleGrantedAuthority[] granted = Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new).toArray(SimpleGrantedAuthority[]::new);
        return jwt().jwt(j -> j.subject(userId.toString())).authorities(granted);
    }

    private RequestPostProcessor validVerifier() {
        return token(verifierId, "ROLE_LEGAL_VERIFIER", "LEGAL_REVIEW");
    }

    @Test
    void noToken_returns401() throws Exception {
        mockMvc.perform(post(URL, requestId)).andExpect(status().isUnauthorized());
        verifyNoInteractions(legalVerificationService);
    }

    @Test
    void ownerExecutorOrVerifierWithoutLegalReview_return403() throws Exception {
        for (String[] auth : new String[][]{{"ROLE_OWNER"}, {"ROLE_EXECUTOR"}, {"ROLE_LEGAL_VERIFIER"}}) {
            mockMvc.perform(post(URL, requestId).with(token(UUID.randomUUID(), auth)))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(legalVerificationService);
    }

    @Test
    void invalidUuid_returns400() throws Exception {
        mockMvc.perform(post(URL, "not-a-uuid").with(validVerifier())).andExpect(status().isBadRequest());
        verifyNoInteractions(legalVerificationService);
    }

    @Test
    void notFoundOrConflictOfInterest_returns404() throws Exception {
        when(legalVerificationService.inspectDocumentSignatures(eq(requestId), eq(verifierId)))
                .thenThrow(new VerificationRequestNotFoundException("Request not found: " + requestId));

        mockMvc.perform(post(URL, requestId).with(validVerifier())).andExpect(status().isNotFound());
    }

    @Test
    void pendingNotChecked_returns409() throws Exception {
        when(legalVerificationService.inspectDocumentSignatures(eq(requestId), eq(verifierId)))
                .thenThrow(new VerificationStepRequiredException("Please check the document first."));

        mockMvc.perform(post(URL, requestId).with(validVerifier()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("VERIFICATION_STEP_REQUIRED"));
    }

    @Test
    void validVerifier_returns200WithReport() throws Exception {
        when(legalVerificationService.inspectDocumentSignatures(eq(requestId), eq(verifierId)))
                .thenReturn(PdfSignatureReportDto.builder()
                        .requestId(requestId)
                        .documentKind(PdfSignatureReportDto.DocumentKind.PDF)
                        .overall(PdfSignatureReportDto.Overall.NO_SIGNATURE)
                        .signatures(List.of())
                        .build());

        mockMvc.perform(post(URL, requestId).with(validVerifier()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overall").value("NO_SIGNATURE"))
                .andExpect(jsonPath("$.data.documentKind").value("PDF"));
    }
}
