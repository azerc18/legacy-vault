package com.ltld.app.legacyvault.controller.session;

import com.ltld.app.legacyvault.controller.UserSessionController;
import com.ltld.app.legacyvault.dto.sessiondto.UserSessionResponse;
import com.ltld.app.legacyvault.security.SecurityConfig;
import com.ltld.app.legacyvault.service.sessionservice.UserSessionService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// /api/auth/** là permitAll, riêng /api/auth/sessions/** phải có JWT
@WebMvcTest(UserSessionController.class)
@Import(SecurityConfig.class)
class UserSessionControllerSecurityTest {

    private static final String BASE = "/api/auth/sessions";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private UserSessionService userSessionService;
    @MockitoBean private JwtDecoder jwtDecoder;

    private static RequestPostProcessor token(UUID subject) {
        return jwt().jwt(j -> j.subject(subject.toString()));
    }

    @Test
    void list_noToken_returns401() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        verifyNoInteractions(userSessionService);
    }

    @Test
    void revoke_noToken_returns401() throws Exception {
        mockMvc.perform(delete(BASE + "/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
        verifyNoInteractions(userSessionService);
    }

    @Test
    void revokeOthers_noToken_returns401() throws Exception {
        mockMvc.perform(post(BASE + "/revoke-others")).andExpect(status().isUnauthorized());
        verify(userSessionService, never()).revokeOthers(any(), any());
    }

    @Test
    void list_withToken_passesUserAndCookie() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        when(userSessionService.listActive(userId, "raw-cookie")).thenReturn(List.of(
                UserSessionResponse.builder().id(sessionId).current(true).userAgent("Chrome").build()));

        mockMvc.perform(get(BASE).with(token(userId)).cookie(new Cookie("refresh_token", "raw-cookie")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(sessionId.toString()))
                .andExpect(jsonPath("$.data[0].current").value(true));
    }

    @Test
    void list_withTokenWithoutCookie_passesNullCookie() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userSessionService.listActive(eq(userId), isNull())).thenReturn(List.of());

        mockMvc.perform(get(BASE).with(token(userId))).andExpect(status().isOk());
        verify(userSessionService).listActive(userId, null);
    }

    @Test
    void revoke_withToken_revokesForCurrentUserOnly() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        mockMvc.perform(delete(BASE + "/" + sessionId).with(token(userId))).andExpect(status().isOk());
        verify(userSessionService).revoke(userId, sessionId);
    }

    @Test
    void revokeOthers_withToken_keepsCurrentCookie() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userSessionService.revokeOthers(userId, "raw-cookie")).thenReturn(2);

        mockMvc.perform(post(BASE + "/revoke-others").with(token(userId)).cookie(new Cookie("refresh_token", "raw-cookie")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(2));
    }
}
