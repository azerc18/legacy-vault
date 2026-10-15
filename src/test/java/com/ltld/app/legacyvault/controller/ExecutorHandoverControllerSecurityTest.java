package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.executordto.VaultHandoverProgressResponse;
import com.ltld.app.legacyvault.enums.HandoverStatus;
import com.ltld.app.legacyvault.exception.ExecutorAssignmentNotFoundException;
import com.ltld.app.legacyvault.exception.ExecutorNotActiveException;
import com.ltld.app.legacyvault.security.SecurityConfig;
import com.ltld.app.legacyvault.service.dmsservice.DmsAutomationService;
import com.ltld.app.legacyvault.service.executorservice.ExecutorAssetService;
import com.ltld.app.legacyvault.service.executorservice.ExecutorHandoverService;
import com.ltld.app.legacyvault.service.executorservice.ExecutorVerificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExecutorController.class)
@Import(SecurityConfig.class)
class ExecutorHandoverControllerSecurityTest {

    private static final String PROGRESS_URL = "/api/executor-assignments/{id}/handover-progress";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private DmsAutomationService dmsAutomationService;
    @MockitoBean private ExecutorAssetService executorAssetService;
    @MockitoBean private ExecutorHandoverService executorHandoverService;
    @MockitoBean private ExecutorVerificationService executorVerificationService;
    @MockitoBean private JwtDecoder jwtDecoder;

    private static RequestPostProcessor token(UUID userId) {
        return jwt().jwt(j -> j.subject(userId.toString()));
    }

    @Test
    void handoverProgress_noToken_returns401() throws Exception {
        mockMvc.perform(get(PROGRESS_URL, UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(executorHandoverService);
    }

    @Test
    void handoverProgress_validRequest_returns200WithStatuses() throws Exception {
        UUID assignmentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID vaultId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        VaultHandoverProgressResponse item = VaultHandoverProgressResponse.builder()
                .vaultId(vaultId)
                .vaultName("Két gia đình")
                .handoverStatus(HandoverStatus.REJECTED)
                .requestId(requestId)
                .requestedAt(LocalDateTime.of(2026, 10, 1, 9, 0))
                .decidedAt(LocalDateTime.of(2026, 10, 2, 10, 0))
                .rejectionReason("Giấy chứng tử không rõ")
                .build();
        when(executorHandoverService.getHandoverProgress(eq(assignmentId), eq(userId.toString())))
                .thenReturn(List.of(item));

        mockMvc.perform(get(PROGRESS_URL, assignmentId).with(token(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].vaultId").value(vaultId.toString()))
                .andExpect(jsonPath("$.data[0].vaultName").value("Két gia đình"))
                .andExpect(jsonPath("$.data[0].handoverStatus").value("REJECTED"))
                .andExpect(jsonPath("$.data[0].requestId").value(requestId.toString()))
                .andExpect(jsonPath("$.data[0].rejectionReason").value("Giấy chứng tử không rõ"))
                .andExpect(content().string(not(containsString("email"))))
                .andExpect(content().string(not(containsString("deathCertificate"))))
                .andExpect(content().string(not(containsString("encryptedSecret"))));
    }

    @Test
    void handoverProgress_noRequestsYet_returns200WithEmptyList() throws Exception {
        UUID assignmentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(executorHandoverService.getHandoverProgress(eq(assignmentId), eq(userId.toString())))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get(PROGRESS_URL, assignmentId).with(token(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void handoverProgress_inactiveAssignment_returns403() throws Exception {
        UUID assignmentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(executorHandoverService.getHandoverProgress(eq(assignmentId), eq(userId.toString())))
                .thenThrow(new ExecutorNotActiveException("Chưa đến thời điểm thực hiện quyền"));

        mockMvc.perform(get(PROGRESS_URL, assignmentId).with(token(userId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Chưa đến thời điểm thực hiện quyền"));
    }

    @Test
    void handoverProgress_assignmentNotFound_returns404() throws Exception {
        UUID assignmentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(executorHandoverService.getHandoverProgress(eq(assignmentId), eq(userId.toString())))
                .thenThrow(new ExecutorAssignmentNotFoundException("Executor assignment not found."));

        mockMvc.perform(get(PROGRESS_URL, assignmentId).with(token(userId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void handoverProgress_callerMismatchIdor_returns404Not403() throws Exception {
        UUID assignmentId = UUID.randomUUID();
        UUID attackerId = UUID.randomUUID();
        when(executorHandoverService.getHandoverProgress(eq(assignmentId), eq(attackerId.toString())))
                .thenThrow(new ExecutorAssignmentNotFoundException("Executor assignment not found."));

        mockMvc.perform(get(PROGRESS_URL, assignmentId).with(token(attackerId)))
                .andExpect(status().isNotFound());
    }
}
