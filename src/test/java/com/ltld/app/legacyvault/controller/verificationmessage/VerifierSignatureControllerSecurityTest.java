package com.ltld.app.legacyvault.controller.verificationmessage;

import com.ltld.app.legacyvault.controller.LegalVerificationController;
import com.ltld.app.legacyvault.dto.legalverificationdto.VerifierSignatureDto;
import com.ltld.app.legacyvault.enums.SignatureSealStatus;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Review #293 m2: security test tầng HTTP cho GET /api/verifier/requests/{id}/signature (V3). */
@WebMvcTest(LegalVerificationController.class)
@Import(SecurityConfig.class)
class VerifierSignatureControllerSecurityTest {

    private static final String URL = "/api/verifier/requests/{id}/signature";

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

    @Test
    void noToken_returns401() throws Exception {
        mockMvc.perform(get(URL, requestId)).andExpect(status().isUnauthorized());
        verifyNoInteractions(legalVerificationService);
    }

    @Test
    void ownerExecutorOrVerifierWithoutLegalReview_return403() throws Exception {
        for (String[] auth : new String[][]{{"ROLE_OWNER"}, {"ROLE_EXECUTOR"}, {"ROLE_LEGAL_VERIFIER"}}) {
            mockMvc.perform(get(URL, requestId).with(token(UUID.randomUUID(), auth)))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(legalVerificationService);
    }

    @Test
    void invalidUuid_returns400() throws Exception {
        mockMvc.perform(get(URL, "not-a-uuid").with(token(verifierId, "ROLE_LEGAL_VERIFIER", "LEGAL_REVIEW")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(legalVerificationService);
    }

    @Test
    void validVerifier_returns200WithCheckResult() throws Exception {
        when(legalVerificationService.getSignature(eq(requestId), eq(verifierId)))
                .thenReturn(VerifierSignatureDto.builder()
                        .requestId(requestId)
                        .status(SignatureSealStatus.VALID)
                        .signatureValid(true)
                        .publicKeyPem("-----BEGIN PUBLIC KEY-----\nAAA\n-----END PUBLIC KEY-----\n")
                        .build());

        mockMvc.perform(get(URL, requestId).with(token(verifierId, "ROLE_LEGAL_VERIFIER", "LEGAL_REVIEW")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VALID"))
                .andExpect(jsonPath("$.data.signatureValid").value(true))
                .andExpect(jsonPath("$.data.privateKeyEncrypted").doesNotExist());
    }

    @Test
    void notSignedYet_returns404() throws Exception {
        when(legalVerificationService.getSignature(eq(requestId), eq(verifierId)))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "This request has not been signed yet."));

        mockMvc.perform(get(URL, requestId).with(token(verifierId, "ROLE_LEGAL_VERIFIER", "LEGAL_REVIEW")))
                .andExpect(status().isNotFound());
    }
}
