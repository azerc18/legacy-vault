package com.ltld.app.legacyvault.controller.owner;

import com.ltld.app.legacyvault.controller.OwnerExecutorController;
import com.ltld.app.legacyvault.dto.executordto.ExecutorAssignmentResponse;
import com.ltld.app.legacyvault.security.SecurityConfig;
import com.ltld.app.legacyvault.service.executorservice.ExecutorManagementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OwnerExecutorController.class)
@Import(SecurityConfig.class)
class OwnerExecutorControllerSecurityTest {

    private static final String BODY = "{\"email\":\"exec@example.com\"}";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ExecutorManagementService executorManagementService;
    @MockitoBean private JwtDecoder jwtDecoder; // SecurityConfig cần có JwtDecoder

    private static RequestPostProcessor token(String... authorities) {
        SimpleGrantedAuthority[] granted = Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new).toArray(SimpleGrantedAuthority[]::new);
        return jwt().jwt(j -> j.subject(UUID.randomUUID().toString())).authorities(granted);
    }

    @Test
    void list_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/owner/executors"))
                .andExpect(status().isUnauthorized());
        verify(executorManagementService, never()).listExecutors(any());
    }

    @Test
    void list_executorWithoutExecutorManage_returns403() throws Exception {
        mockMvc.perform(get("/api/owner/executors")
                        .with(token("ROLE_EXECUTOR", "VAULT_READ", "ASSET_DOWNLOAD")))
                .andExpect(status().isForbidden());
        verify(executorManagementService, never()).listExecutors(any());
    }

    @Test
    void list_ownerWithExecutorManage_returns200() throws Exception {
        when(executorManagementService.listExecutors(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/owner/executors")
                        .with(token("ROLE_OWNER", "EXECUTOR_MANAGE")))
                .andExpect(status().isOk());
    }

    @Test
    void assign_noToken_returns401() throws Exception {
        mockMvc.perform(put("/api/owner/executors/{t}", "PRIMARY")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized());
        verify(executorManagementService, never()).assignExecutor(any(), any(), any());
    }

    @Test
    void assign_executorWithoutExecutorManage_returns403() throws Exception {
        mockMvc.perform(put("/api/owner/executors/{t}", "PRIMARY")
                        .with(token("ROLE_EXECUTOR", "VAULT_READ", "ASSET_DOWNLOAD"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden());
        verify(executorManagementService, never()).assignExecutor(any(), any(), any());
    }

    @Test
    void assign_ownerWithExecutorManage_returns200() throws Exception {
        when(executorManagementService.assignExecutor(any(), any(), any()))
                .thenReturn(ExecutorAssignmentResponse.builder().build());

        mockMvc.perform(put("/api/owner/executors/{t}", "PRIMARY")
                        .with(token("ROLE_OWNER", "EXECUTOR_MANAGE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk());
    }

    @Test
    void revoke_noToken_returns401() throws Exception {
        mockMvc.perform(delete("/api/owner/executors/{t}", "BACKUP"))
                .andExpect(status().isUnauthorized());
        verify(executorManagementService, never()).revokeExecutor(any(), any());
    }

    @Test
    void revoke_beneficiaryOnly_returns403() throws Exception {
        mockMvc.perform(delete("/api/owner/executors/{t}", "BACKUP")
                        .with(token("ROLE_BENEFICIARY", "VAULT_READ")))
                .andExpect(status().isForbidden());
        verify(executorManagementService, never()).revokeExecutor(any(), any());
    }

    @Test
    void revoke_ownerWithExecutorManage_returns200() throws Exception {
        when(executorManagementService.revokeExecutor(any(), any()))
                .thenReturn(ExecutorAssignmentResponse.builder().build());

        mockMvc.perform(delete("/api/owner/executors/{t}", "BACKUP")
                        .with(token("ROLE_OWNER", "EXECUTOR_MANAGE")))
                .andExpect(status().isOk());
    }
}
