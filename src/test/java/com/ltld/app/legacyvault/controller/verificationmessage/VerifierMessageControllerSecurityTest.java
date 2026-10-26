package com.ltld.app.legacyvault.controller.verificationmessage;

import com.ltld.app.legacyvault.controller.LegalVerificationController;
import com.ltld.app.legacyvault.controller.VerifierMessageController;
import com.ltld.app.legacyvault.dto.executordto.VerificationMessageResponse;
import com.ltld.app.legacyvault.dto.legalverificationdto.VerificationDocumentDto;
import com.ltld.app.legacyvault.dto.legalverificationdto.VerifierSummaryResponseDto;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    // ---------- S5: GET /summary (số liệu Home, FR-15) ----------

    private static final String SUMMARY_URL = "/api/verifier/requests/summary";

    @Test
    void summary_noToken_returns401() throws Exception {
        mockMvc.perform(get(SUMMARY_URL)).andExpect(status().isUnauthorized());
        verifyNoInteractions(legalVerificationService);
    }

    @Test
    void summary_executorToken_returns403_andServiceNotCalled() throws Exception {
        mockMvc.perform(get(SUMMARY_URL).with(token(UUID.randomUUID(), "ROLE_EXECUTOR")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(legalVerificationService);
    }

    @Test
    void summary_validVerifier_returns200WithCounts() throws Exception {
        when(legalVerificationService.getSummary(eq(verifierId)))
                .thenReturn(VerifierSummaryResponseDto.builder()
                        .pending(3).awaitingSignature(2).signed(150).rejected(7).build());

        mockMvc.perform(get(SUMMARY_URL).with(validVerifier()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.pending").value(3))
                .andExpect(jsonPath("$.data.awaitingSignature").value(2))
                .andExpect(jsonPath("$.data.signed").value(150))
                .andExpect(jsonPath("$.data.rejected").value(7));
    }

    // ---------- FR-12: GET /rejection-reasons ----------

    private static final String REASONS_URL = "/api/verifier/requests/rejection-reasons";

    @Test
    void rejectionReasons_noToken_returns401() throws Exception {
        mockMvc.perform(get(REASONS_URL)).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectionReasons_executorToken_returns403() throws Exception {
        mockMvc.perform(get(REASONS_URL).with(token(UUID.randomUUID(), "ROLE_EXECUTOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectionReasons_validVerifier_returnsAllCodesWithLabels() throws Exception {
        mockMvc.perform(get(REASONS_URL).with(validVerifier()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(6))
                .andExpect(jsonPath("$.data[0].code").value("DOCUMENT_UNREADABLE"))
                .andExpect(jsonPath("$.data[0].label").value("Tài liệu mờ, không đọc được"))
                .andExpect(jsonPath("$.data[5].code").value("OTHER"));
    }

    // ---------- FR-12: validate lý do từ chối ----------

    @Test
    void reject_otherWithoutNote_returns400() throws Exception {
        mockMvc.perform(post("/api/verifier/requests/{id}/reject", requestId).with(validVerifier())
                        .contentType("application/json").content("{\"reason\":\"OTHER\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(legalVerificationService);
    }

    @Test
    void reject_missingReason_returns400() throws Exception {
        mockMvc.perform(post("/api/verifier/requests/{id}/reject", requestId).with(validVerifier())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reject_otherWithNote_returns200() throws Exception {
        mockMvc.perform(post("/api/verifier/requests/{id}/reject", requestId).with(validVerifier())
                        .contentType("application/json").content("{\"reason\":\"OTHER\",\"note\":\"ly do\"}"))
                .andExpect(status().isOk());
    }

    // ---------- FR-13: check tài liệu là POST ----------

    @Test
    void verifyDocument_getIsNoLongerAllowed() throws Exception {
        mockMvc.perform(get("/api/verifier/requests/{id}/document/verify", requestId).with(validVerifier()))
                .andExpect(status().is4xxClientError());
    }

    // ---------- FR-12: sub sai định dạng -> 401, không phải 500 ----------

    private static RequestPostProcessor badSubjectVerifier() {
        return jwt().jwt(j -> j.subject("khong-phai-uuid"))
                .authorities(new SimpleGrantedAuthority("ROLE_LEGAL_VERIFIER"),
                        new SimpleGrantedAuthority("LEGAL_REVIEW"));
    }

    @Test
    void summary_badSubject_returns401() throws Exception {
        mockMvc.perform(get(SUMMARY_URL).with(badSubjectVerifier()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(legalVerificationService);
    }

    @Test
    void messages_badSubject_returns401() throws Exception {
        mockMvc.perform(get(MESSAGES_URL, requestId).with(badSubjectVerifier()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(verifierMessageService);
    }
}
