package com.ltld.app.legacyvault.controller.user;

import com.ltld.app.legacyvault.controller.AccountDeletionController;
import com.ltld.app.legacyvault.dto.accountdto.AccountDeletionCheckResponse;
import com.ltld.app.legacyvault.security.SecurityConfig;
import com.ltld.app.legacyvault.service.accountservice.AccountDeletionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountDeletionController.class)
@Import(SecurityConfig.class)
class AccountDeletionControllerSecurityTest {

    private static final String BASE = "/api/users/me/account";
    private static final String BODY = "{\"password\":\"pw\",\"confirmEmail\":\"a@example.com\"}";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private AccountDeletionService accountDeletionService;
    @MockitoBean private JwtDecoder jwtDecoder;

    @Test
    void noToken_returns401() throws Exception {
        mockMvc.perform(get(BASE + "/deletion-check")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE + "/delete").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        verify(accountDeletionService, never()).delete(any(), any());
    }

    @Test
    void check_returnsBlockers() throws Exception {
        UUID id = UUID.randomUUID();
        when(accountDeletionService.check(id)).thenReturn(AccountDeletionCheckResponse.builder()
                .deletable(false).blockers(List.of("VAULTS_OWNED")).vaultCount(1).build());

        mockMvc.perform(get(BASE + "/deletion-check").with(jwt().jwt(j -> j.subject(id.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.blockers[0]").value("VAULTS_OWNED"));
    }

    @Test
    void delete_missingPassword_returns400() throws Exception {
        mockMvc.perform(post(BASE + "/delete").with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString())))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"confirmEmail\":\"a@example.com\"}"))
                .andExpect(status().isBadRequest());
        verify(accountDeletionService, never()).delete(any(), any());
    }

    @Test
    void delete_passesCurrentUser() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(post(BASE + "/delete").with(jwt().jwt(j -> j.subject(id.toString())))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());
        verify(accountDeletionService).delete(eq(id), argThat(r -> "pw".equals(r.getPassword())));
    }
}
