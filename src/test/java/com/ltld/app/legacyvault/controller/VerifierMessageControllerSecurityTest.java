package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.executordto.VerificationMessageResponse;
import com.ltld.app.legacyvault.dto.legalverificationdto.VerificationDocumentDto;
import com.ltld.app.legacyvault.enums.ExchangeSenderRole;
import com.ltld.app.legacyvault.security.SecurityConfig;
import com.ltld.app.legacyvault.service.legalverificationservice.LegalVerificationService;
import com.ltld.app.legacyvault.service.legalverificationservice.VerifierMessageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** m1: security test tầng HTTP cho 4 endpoint mới của Legal Verifier (D1, D2). */
@WebMvcTest({LegalVerificationController.class, VerifierMessageController.class})
@Import(SecurityConfig.class)
class VerifierMessageControllerSecurityTest {

    private static final String DOCUMENT_URL = "/api/verifier/requests/{id}/document";
    private static final String MESSAGES_URL = "/api/verifier/requests/{id}/messages";
    private static final String ATTACHMENT_URL = "/api/verifier/requests/{id}/messages/{messageId}/attachment";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private LegalVerificationService legalVerificationService;
    @MockitoBean private VerifierMessageService verifierMessageService;
    @MockitoBean private JwtDecoder jwtDecoder; // SecurityConfig cần có JwtDecoder

    private final UUID requestId = UUID.randomUUID();
    private final UUID messageId = UUID.randomUUID();
    private final UUID verifierId = UUID.randomUUID();

    private static RequestPostProcessor token(UUID userId, String... authorities) {
        SimpleGrantedAuthority[] granted = java.util.Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new).toArray(SimpleGrantedAuthority[]::new);
        return jwt().jwt(j -> j.subject(userId.toString())).authorities(granted);
    }

    private RequestPostProcessor validVerifier() {
        return token(verifierId, "ROLE_LEGAL_VERIFIER", "LEGAL_REVIEW");
    }

    private static MockMultipartFile pdf() {
        return new MockMultipartFile("file", "extra.pdf", "application/pdf", "%PDF-1.4".getBytes());
    }

    private VerificationMessageResponse response() {
        return VerificationMessageResponse.builder()
                .id(UUID.randomUUID())
                .requestId(requestId)
                .senderRole(ExchangeSenderRole.VERIFIER)
                .senderName("Verifier Name")
                .content("Bổ sung giấy tờ")
                .createdAt(LocalDateTime.of(2026, 10, 16, 21, 0))
                .build();
    }

    private void callAllFourEndpoints(RequestPostProcessor auth, int expectedStatus) throws Exception {
        var doc = get(DOCUMENT_URL, requestId);
        var history = get(MESSAGES_URL, requestId);
        var send = multipart(MESSAGES_URL, requestId).param("content", "x");
        var attachment = get(ATTACHMENT_URL, requestId, messageId);
        if (auth != null) {
            doc.with(auth);
            history.with(auth);
            send.with(auth);
            attachment.with(auth);
        }
        mockMvc.perform(doc).andExpect(status().is(expectedStatus));
        mockMvc.perform(history).andExpect(status().is(expectedStatus));
        mockMvc.perform(send).andExpect(status().is(expectedStatus));
        mockMvc.perform(attachment).andExpect(status().is(expectedStatus));
    }

    // ---------- S1: không token ----------

    @Test
    void allFourEndpoints_noToken_return401() throws Exception {
        callAllFourEndpoints(null, 401);
        verifyNoInteractions(legalVerificationService, verifierMessageService);
    }

    // ---------- S2: sai vai trò / thiếu LEGAL_REVIEW ----------

    @Test
    void allFourEndpoints_executorToken_return403_andServiceNotCalled() throws Exception {
        callAllFourEndpoints(token(UUID.randomUUID(), "ROLE_EXECUTOR"), 403);
        verifyNoInteractions(legalVerificationService, verifierMessageService);
    }

    @Test
    void allFourEndpoints_ownerToken_return403_andServiceNotCalled() throws Exception {
        callAllFourEndpoints(token(UUID.randomUUID(), "ROLE_OWNER"), 403);
        verifyNoInteractions(legalVerificationService, verifierMessageService);
    }

    @Test
    void allFourEndpoints_verifierRoleWithoutLegalReview_return403() throws Exception {
        callAllFourEndpoints(token(UUID.randomUUID(), "ROLE_LEGAL_VERIFIER"), 403);
        verifyNoInteractions(legalVerificationService, verifierMessageService);
    }

    // ---------- S3: Verifier hợp lệ tải tài liệu đã giải mã ----------

    @Test
    void document_validVerifier_returns200WithSafeHeaders() throws Exception {
        when(legalVerificationService.getDocument(eq(requestId), eq(verifierId)))
                .thenReturn(VerificationDocumentDto.builder()
                        .content("%PDF-1.4 noi dung".getBytes())
                        .contentType("application/pdf")
                        .fileName("giay-chung-tu.pdf")
                        .build());

        mockMvc.perform(get(DOCUMENT_URL, requestId).with(validVerifier()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Content-Disposition", startsWith("inline")));
    }

    @Test
    void attachment_validVerifier_returns200WithSafeHeaders() throws Exception {
        when(verifierMessageService.getAttachment(eq(requestId), eq(messageId), eq(verifierId)))
                .thenReturn(VerificationDocumentDto.builder()
                        .content("%PDF-1.4 bosung".getBytes())
                        .contentType("application/pdf")
                        .fileName("bosung.pdf")
                        .build());

        mockMvc.perform(get(ATTACHMENT_URL, requestId, messageId).with(validVerifier()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Content-Disposition", startsWith("inline")));
    }

    // ---------- S4: gửi tin nhắn dạng multipart ----------

    @Test
    void send_contentOnly_returns201() throws Exception {
        when(verifierMessageService.send(eq(requestId), eq(verifierId), any(), any()))
                .thenReturn(response());

        mockMvc.perform(multipart(MESSAGES_URL, requestId)
                        .param("content", "Bổ sung giấy tờ").with(validVerifier()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.senderRole").value("VERIFIER"));
    }

    @Test
    void send_fileOnly_returns201() throws Exception {
        when(verifierMessageService.send(eq(requestId), eq(verifierId), any(), any()))
                .thenReturn(response());

        mockMvc.perform(multipart(MESSAGES_URL, requestId).file(pdf()).with(validVerifier()))
                .andExpect(status().isCreated());
    }

    @Test
    void send_textLongerThan2000Characters_returns400_withoutCallingService() throws Exception {
        mockMvc.perform(multipart(MESSAGES_URL, requestId)
                        .param("content", "a".repeat(2001)).with(validVerifier()))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(verifierMessageService);
    }
}