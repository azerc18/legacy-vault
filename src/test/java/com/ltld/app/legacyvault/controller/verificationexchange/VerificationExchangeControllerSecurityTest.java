package com.ltld.app.legacyvault.controller.verificationexchange;

import com.ltld.app.legacyvault.controller.ExecutorController;
import com.ltld.app.legacyvault.dto.executordto.VerificationMessageResponse;
import com.ltld.app.legacyvault.dto.legalverificationdto.VerificationDocumentDto;
import com.ltld.app.legacyvault.enums.ExchangeSenderRole;
import com.ltld.app.legacyvault.exception.ExecutorAssignmentNotFoundException;
import com.ltld.app.legacyvault.exception.ExecutorNotActiveException;
import com.ltld.app.legacyvault.exception.InvalidVerificationDocumentException;
import com.ltld.app.legacyvault.exception.VerificationRequestClosedException;
import com.ltld.app.legacyvault.exception.VerificationRequestNotFoundException;
import com.ltld.app.legacyvault.security.SecurityConfig;
import com.ltld.app.legacyvault.service.dmsservice.DmsAutomationService;
import com.ltld.app.legacyvault.service.executorservice.ExecutorAssetService;
import com.ltld.app.legacyvault.service.executorservice.ExecutorHandoverService;
import com.ltld.app.legacyvault.service.executorservice.ExecutorVerificationService;
import com.ltld.app.legacyvault.service.executorservice.VerificationExchangeService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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

@WebMvcTest(ExecutorController.class)
@Import(SecurityConfig.class)
class VerificationExchangeControllerSecurityTest {

    private static final String MESSAGES_URL =
            "/api/executor-assignments/{assignmentId}/verification-requests/{requestId}/messages";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private DmsAutomationService dmsAutomationService;
    @MockitoBean private ExecutorAssetService executorAssetService;
    @MockitoBean private ExecutorHandoverService executorHandoverService;
    @MockitoBean private ExecutorVerificationService executorVerificationService;
    @MockitoBean private VerificationExchangeService verificationExchangeService;
    @MockitoBean private com.ltld.app.legacyvault.service.executorservice.ExecutorSelfService executorSelfService;
    @MockitoBean private JwtDecoder jwtDecoder;

    private final UUID assignmentId = UUID.randomUUID();
    private final UUID requestId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    private static RequestPostProcessor token(UUID userId) {
        return jwt().jwt(j -> j.subject(userId.toString()));
    }

    private static MockMultipartFile pdf() {
        return new MockMultipartFile("file", "extra.pdf", "application/pdf", "%PDF-1.4".getBytes());
    }

    private VerificationMessageResponse response() {
        return VerificationMessageResponse.builder()
                .id(UUID.randomUUID())
                .requestId(requestId)
                .senderRole(ExchangeSenderRole.EXECUTOR)
                .senderName("Executor Name")
                .content("Bổ sung")
                .attachmentName("extra.pdf")
                .attachmentSize(8L)
                .createdAt(LocalDateTime.of(2026, 10, 5, 8, 30))
                .build();
    }

    @Test
    void send_noToken_returns401() throws Exception {
        mockMvc.perform(multipart(MESSAGES_URL, assignmentId, requestId).file(pdf()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(verificationExchangeService);
    }

    @Test
    void send_withFileAndText_returns201_andPassesJwtSubject() throws Exception {
        when(verificationExchangeService.send(eq(assignmentId), eq(requestId), eq(userId.toString()), any(), any()))
                .thenReturn(response());

        mockMvc.perform(multipart(MESSAGES_URL, assignmentId, requestId).file(pdf())
                        .param("content", "Bổ sung").with(token(userId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.senderRole").value("EXECUTOR"))
                .andExpect(jsonPath("$.data.attachmentName").value("extra.pdf"))
                .andExpect(content().string(not(containsString("attachmentPath"))))
                .andExpect(content().string(not(containsString("email"))));
    }

    @Test
    void send_textOnlyWithoutFilePart_returns201() throws Exception {
        when(verificationExchangeService.send(eq(assignmentId), eq(requestId), eq(userId.toString()), any(), any()))
                .thenReturn(response());

        mockMvc.perform(multipart(MESSAGES_URL, assignmentId, requestId)
                        .param("content", "Chỉ có chữ").with(token(userId)))
                .andExpect(status().isCreated());
    }

    @Test
    void send_textLongerThan2000Characters_returns400_withoutCallingService() throws Exception {
        mockMvc.perform(multipart(MESSAGES_URL, assignmentId, requestId)
                        .param("content", "a".repeat(2001)).with(token(userId)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(verificationExchangeService);
    }

    @Test
    void send_invalidAttachment_returns400() throws Exception {
        when(verificationExchangeService.send(any(), any(), any(), any(), any()))
                .thenThrow(new InvalidVerificationDocumentException("Only pdf, png, jpg files are allowed."));

        mockMvc.perform(multipart(MESSAGES_URL, assignmentId, requestId).file(pdf()).with(token(userId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void send_inactiveAssignment_returns403() throws Exception {
        when(verificationExchangeService.send(any(), any(), any(), any(), any()))
                .thenThrow(new ExecutorNotActiveException("Access is not available yet"));

        mockMvc.perform(multipart(MESSAGES_URL, assignmentId, requestId).param("content", "x").with(token(userId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void send_unknownAssignmentOrForeignRequest_returns404() throws Exception {
        when(verificationExchangeService.send(any(), any(), any(), any(), any()))
                .thenThrow(new ExecutorAssignmentNotFoundException("Executor assignment not found."))
                .thenThrow(new VerificationRequestNotFoundException("Verification request not found."));

        mockMvc.perform(multipart(MESSAGES_URL, assignmentId, requestId).param("content", "x").with(token(userId)))
                .andExpect(status().isNotFound());
        mockMvc.perform(multipart(MESSAGES_URL, assignmentId, requestId).param("content", "x").with(token(userId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void send_closedRequest_returns409() throws Exception {
        when(verificationExchangeService.send(any(), any(), any(), any(), any()))
                .thenThrow(new VerificationRequestClosedException("This verification request is closed."));

        mockMvc.perform(multipart(MESSAGES_URL, assignmentId, requestId).param("content", "x").with(token(userId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void history_noToken_returns401() throws Exception {
        mockMvc.perform(get(MESSAGES_URL, assignmentId, requestId))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(verificationExchangeService);
    }

    @Test
    void history_returns200WithMessages() throws Exception {
        when(verificationExchangeService.history(assignmentId, requestId, userId.toString(), 1, 10))
                .thenReturn(new PageImpl<>(List.of(response())));

        mockMvc.perform(get(MESSAGES_URL, assignmentId, requestId).param("page", "1").param("size", "10")
                        .with(token(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].content").value("Bổ sung"))
                .andExpect(jsonPath("$.data.content[0].senderName").value("Executor Name"));
    }

    @Test
    void history_empty_returns200WithEmptyList() throws Exception {
        when(verificationExchangeService.history(assignmentId, requestId, userId.toString(), 0, 50))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        mockMvc.perform(get(MESSAGES_URL, assignmentId, requestId).with(token(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty());
    }

    @Test
    void history_foreignRequest_returns404Not403() throws Exception {
        when(verificationExchangeService.history(any(), any(), any(), anyInt(), anyInt()))
                .thenThrow(new VerificationRequestNotFoundException("Verification request not found."));

        mockMvc.perform(get(MESSAGES_URL, assignmentId, requestId).with(token(userId)))
                .andExpect(status().isNotFound());
    }

    // ---------- B5: tải tệp đính kèm ----------

    private static final String ATTACHMENT_URL = MESSAGES_URL + "/{messageId}/attachment";
    private final UUID messageId = UUID.randomUUID();

    @Test
    void attachment_noToken_returns401() throws Exception {
        mockMvc.perform(get(ATTACHMENT_URL, assignmentId, requestId, messageId))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(verificationExchangeService);
    }

    @Test
    void attachment_returnsBytesWithSafeHeaders_andPassesJwtSubject() throws Exception {
        byte[] pdf = "%PDF-1.4".getBytes();
        when(verificationExchangeService.getAttachment(assignmentId, requestId, messageId, userId.toString()))
                .thenReturn(VerificationDocumentDto.builder()
                        .content(pdf).contentType("application/pdf").fileName("giấy tờ.pdf").build());

        mockMvc.perform(get(ATTACHMENT_URL, assignmentId, requestId, messageId).with(token(userId)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(content().bytes(pdf))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Content-Disposition", containsString("inline")));
    }

    @Test
    void attachment_inactiveAssignment_returns403() throws Exception {
        when(verificationExchangeService.getAttachment(any(), any(), any(), any()))
                .thenThrow(new ExecutorNotActiveException("Access is not available yet"));

        mockMvc.perform(get(ATTACHMENT_URL, assignmentId, requestId, messageId).with(token(userId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void attachment_foreignAssignmentOrMissingAttachment_returns404Not403() throws Exception {
        when(verificationExchangeService.getAttachment(any(), any(), any(), any()))
                .thenThrow(new ExecutorAssignmentNotFoundException("Executor assignment not found."))
                .thenThrow(new VerificationRequestNotFoundException("Attachment not found."));

        mockMvc.perform(get(ATTACHMENT_URL, assignmentId, requestId, messageId).with(token(userId)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(ATTACHMENT_URL, assignmentId, requestId, messageId).with(token(userId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void attachment_invalidMessageId_returns400() throws Exception {
        mockMvc.perform(get(ATTACHMENT_URL, assignmentId, requestId, "not-a-uuid").with(token(userId)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(verificationExchangeService);
    }
}
