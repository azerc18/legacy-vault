package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.executordto.ExecutorActivationResponse;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.ExecutorType;
import com.ltld.app.legacyvault.exception.DmsNotTriggeredException;
import com.ltld.app.legacyvault.exception.ExecutorAssignmentNotFoundException;
import com.ltld.app.legacyvault.exception.ExecutorNotActiveException;
import com.ltld.app.legacyvault.security.SecurityConfig;
import com.ltld.app.legacyvault.service.dmsservice.DmsAutomationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExecutorController.class)
@Import(SecurityConfig.class)
class ExecutorControllerSecurityTest {

    private static final String ACKNOWLEDGE_URL = "/api/executor-assignments/{id}/acknowledge";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private DmsAutomationService dmsAutomationService;
    @MockitoBean private com.ltld.app.legacyvault.service.executorservice.ExecutorVerificationService executorVerificationService;
    @MockitoBean private JwtDecoder jwtDecoder; // SecurityConfig cần có JwtDecoder

    private static RequestPostProcessor token(UUID userId) {
        return jwt().jwt(j -> j.subject(userId.toString()));
    }

    @Test
    void acknowledge_noToken_returns401() throws Exception {
        mockMvc.perform(post(ACKNOWLEDGE_URL, UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(dmsAutomationService);
    }

    @Test
    void acknowledge_withJwt_passesSubjectAsLogin() throws Exception {
        UUID assignmentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(dmsAutomationService.acknowledgeActivation(eq(assignmentId), eq(userId.toString())))
                .thenReturn(ExecutorActivationResponse.builder()
                        .id(assignmentId)
                        .executorType(ExecutorType.PRIMARY)
                        .status(ExecutorStatus.ACTIVE)
                        .acknowledgedAt(LocalDateTime.now())
                        .build());

        mockMvc.perform(post(ACKNOWLEDGE_URL, assignmentId).with(token(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(assignmentId.toString()));
        verify(dmsAutomationService).acknowledgeActivation(assignmentId, userId.toString());
    }

    @Test
    void acknowledge_notActive_returns403() throws Exception {
        when(dmsAutomationService.acknowledgeActivation(any(), any()))
                .thenThrow(new ExecutorNotActiveException("Executor chưa được kích hoạt"));

        mockMvc.perform(post(ACKNOWLEDGE_URL, UUID.randomUUID()).with(token(UUID.randomUUID())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void acknowledge_assignmentNotFound_returns404() throws Exception {
        when(dmsAutomationService.acknowledgeActivation(any(), any()))
                .thenThrow(new ExecutorAssignmentNotFoundException("Không tìm thấy phân công Executor"));

        mockMvc.perform(post(ACKNOWLEDGE_URL, UUID.randomUUID()).with(token(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void acknowledge_dmsNotTriggered_returns409() throws Exception {
        when(dmsAutomationService.acknowledgeActivation(any(), any()))
                .thenThrow(new DmsNotTriggeredException("Dead Man's Switch chưa được kích hoạt"));

        mockMvc.perform(post(ACKNOWLEDGE_URL, UUID.randomUUID()).with(token(UUID.randomUUID())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ==========================================
    // FR-08: Submit Death Verification Endpoint
    // ==========================================

    private static final String SUBMIT_VERIFICATION_URL = "/api/executor-assignments/{id}/verification-requests";

    @Test
    void submitVerification_noToken_returns401() throws Exception {
        org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile(
                "file", "cert.pdf", "application/pdf", "%PDF".getBytes());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(SUBMIT_VERIFICATION_URL, UUID.randomUUID())
                        .file(file)
                        .param("vaultIds", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void submitVerification_happyPath_returns201() throws Exception {
        UUID assignmentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID vaultId = UUID.randomUUID();

        com.ltld.app.legacyvault.dto.executordto.DeathVerificationResponse mockResponse =
                com.ltld.app.legacyvault.dto.executordto.DeathVerificationResponse.builder()
                        .id(UUID.randomUUID())
                        .assignmentId(assignmentId)
                        .status(com.ltld.app.legacyvault.enums.LegalVerificationStatus.PENDING)
                        .vaultIds(List.of(vaultId))
                        .notes("Sample notes")
                        .createdAt(LocalDateTime.now())
                        .build();

        when(executorVerificationService.submit(eq(assignmentId), eq(userId.toString()), any(), any()))
                .thenReturn(mockResponse);

        org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile(
                "file", "cert.pdf", "application/pdf", "%PDF".getBytes());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(SUBMIT_VERIFICATION_URL, assignmentId)
                        .file(file)
                        .param("vaultIds", vaultId.toString())
                        .param("notes", "Sample notes")
                        .with(token(userId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.assignmentId").value(assignmentId.toString()));
    }

    @Test
    void submitVerification_invalidFile_returns400() throws Exception {
        UUID assignmentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(executorVerificationService.submit(any(), any(), any(), any()))
                .thenThrow(new com.ltld.app.legacyvault.exception.InvalidVerificationDocumentException("File exceeds the 10MB limit."));

        org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile(
                "file", "cert.pdf", "application/pdf", "%PDF".getBytes());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(SUBMIT_VERIFICATION_URL, assignmentId)
                        .file(file)
                        .param("vaultIds", UUID.randomUUID().toString())
                        .with(token(userId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void submitVerification_duplicateActive_returns409() throws Exception {
        UUID assignmentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(executorVerificationService.submit(any(), any(), any(), any()))
                .thenThrow(new com.ltld.app.legacyvault.exception.DuplicateVerificationRequestException("A pending verification request already exists."));

        org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile(
                "file", "cert.pdf", "application/pdf", "%PDF".getBytes());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(SUBMIT_VERIFICATION_URL, assignmentId)
                        .file(file)
                        .param("vaultIds", UUID.randomUUID().toString())
                        .with(token(userId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void submitVerification_inactiveExecutor_returns403() throws Exception {
        UUID assignmentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(executorVerificationService.submit(any(), any(), any(), any()))
                .thenThrow(new ExecutorNotActiveException("Executor chưa được kích hoạt"));

        org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile(
                "file", "cert.pdf", "application/pdf", "%PDF".getBytes());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(SUBMIT_VERIFICATION_URL, assignmentId)
                        .file(file)
                        .param("vaultIds", UUID.randomUUID().toString())
                        .with(token(userId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void submitVerification_assignmentNotFound_returns404() throws Exception {
        UUID assignmentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(executorVerificationService.submit(any(), any(), any(), any()))
                .thenThrow(new ExecutorAssignmentNotFoundException("Phân công không tồn tại"));

        org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile(
                "file", "cert.pdf", "application/pdf", "%PDF".getBytes());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(SUBMIT_VERIFICATION_URL, assignmentId)
                        .file(file)
                        .param("vaultIds", UUID.randomUUID().toString())
                        .with(token(userId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void submitVerification_missingFilePart_returns400() throws Exception {
        UUID assignmentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        // Perform multipart request WITHOUT the "file" part
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(SUBMIT_VERIFICATION_URL, assignmentId)
                        .param("vaultIds", UUID.randomUUID().toString())
                        .with(token(userId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void submitVerification_emptyVaultIds_returns400() throws Exception {
        UUID assignmentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile(
                "file", "cert.pdf", "application/pdf", "%PDF".getBytes());

        // Perform multipart request with empty vaultIds
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(SUBMIT_VERIFICATION_URL, assignmentId)
                        .file(file)
                        .with(token(userId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
