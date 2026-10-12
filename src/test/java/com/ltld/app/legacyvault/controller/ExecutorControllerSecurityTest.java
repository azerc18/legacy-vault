package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.security.SecurityConfig;
import com.ltld.app.legacyvault.service.dmsservice.DmsAutomationService;
import com.ltld.app.legacyvault.service.executorservice.ExecutorHandoverService;
import com.ltld.app.legacyvault.service.executorservice.VerificationExchangeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExecutorController.class)
@Import(SecurityConfig.class)
class ExecutorControllerSecurityTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private DmsAutomationService dmsAutomationService;
    @MockitoBean private ExecutorHandoverService executorHandoverService;
    @MockitoBean private VerificationExchangeService verificationExchangeService;
    @MockitoBean private JwtDecoder jwtDecoder; // SecurityConfig cần có JwtDecoder

    private static RequestPostProcessor token(String subject, String... authorities) {
        SimpleGrantedAuthority[] granted = java.util.Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new).toArray(SimpleGrantedAuthority[]::new);
        return jwt().jwt(j -> j.subject(subject)).authorities(granted);
    }

    @Test
    void assignedVaults_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/executors/assigned-vaults"))
                .andExpect(status().isUnauthorized());
        verify(executorHandoverService, never()).getAssignedVaults(any());
    }

    @Test
    void assignedVaults_ownerOnly_returns403() throws Exception {
        mockMvc.perform(get("/api/executors/assigned-vaults")
                        .with(token(UUID.randomUUID().toString(), "ROLE_OWNER", "VAULT_READ")))
                .andExpect(status().isForbidden());
        verify(executorHandoverService, never()).getAssignedVaults(any());
    }

    @Test
    void assignedVaults_executor_returns200AndPassesJwtSubject() throws Exception {
        String subject = UUID.randomUUID().toString();
        when(executorHandoverService.getAssignedVaults(eq(subject))).thenReturn(List.of());

        mockMvc.perform(get("/api/executors/assigned-vaults")
                        .with(token(subject, "ROLE_EXECUTOR")))
                .andExpect(status().isOk());
        verify(executorHandoverService).getAssignedVaults(subject);
    }
}
