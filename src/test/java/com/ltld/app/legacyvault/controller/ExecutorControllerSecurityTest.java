package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.executordto.ExecutorActivationResponse;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.ExecutorType;
import com.ltld.app.legacyvault.exception.DmsNotTriggeredException;
import com.ltld.app.legacyvault.exception.ExecutorAssignmentNotFoundException;
import com.ltld.app.legacyvault.exception.ExecutorNotActiveException;
import com.ltld.app.legacyvault.security.SecurityConfig;
import com.ltld.app.legacyvault.service.dmsservice.DmsAutomationService;
import com.ltld.app.legacyvault.service.executorservice.ExecutorAssetService;
import com.ltld.app.legacyvault.service.executorservice.ExecutorHandoverService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
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
    @MockitoBean private ExecutorAssetService executorAssetService;
    @MockitoBean private ExecutorHandoverService executorHandoverService;
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
}
