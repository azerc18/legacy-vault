package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.security.SecurityConfig;
import com.ltld.app.legacyvault.service.auditservice.OwnerActivityLogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OwnerActivityLogController.class)
@Import(SecurityConfig.class)
class OwnerActivityLogControllerTest {

    private static final String URL = "/api/owner/activity-logs";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private OwnerActivityLogService ownerActivityLogService;
    @MockitoBean private JwtDecoder jwtDecoder; // SecurityConfig cần có JwtDecoder

    private final UUID ownerId = UUID.randomUUID();

    private RequestPostProcessor token(String authority) {
        return jwt().jwt(j -> j.subject(ownerId.toString()))
                .authorities(new SimpleGrantedAuthority(authority));
    }

    @Test
    void getOwnerLogs_noToken_returns401() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(ownerActivityLogService);
    }

    @Test
    void getOwnerLogs_beneficiaryOnly_returns403() throws Exception {
        mockMvc.perform(get(URL).with(token("ROLE_BENEFICIARY")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(ownerActivityLogService);
    }

    @Test
    void getOwnerLogs_executorOnly_returns403() throws Exception {
        mockMvc.perform(get(URL).with(token("ROLE_EXECUTOR")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(ownerActivityLogService);
    }

    @Test
    void getOwnerLogs_negativePage_returns400() throws Exception {
        mockMvc.perform(get(URL).param("page", "-1").with(token("ROLE_OWNER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verifyNoInteractions(ownerActivityLogService);
    }

    @Test
    void getOwnerLogs_sizeZero_returns400() throws Exception {
        mockMvc.perform(get(URL).param("size", "0").with(token("ROLE_OWNER")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(ownerActivityLogService);
    }

    @Test
    void getOwnerLogs_sizeAbove100_returns400() throws Exception {
        mockMvc.perform(get(URL).param("size", "101").with(token("ROLE_OWNER")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(ownerActivityLogService);
    }

    @Test
    void getOwnerLogs_defaults_returns200WithOwnerIdFromJwt() throws Exception {
        when(ownerActivityLogService.getOwnerActivityLogs(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get(URL).with(token("ROLE_OWNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(ownerActivityLogService).getOwnerActivityLogs(
                eq(ownerId), isNull(), isNull(), eq(PageRequest.of(0, 20)));
    }

    @Test
    void getOwnerLogs_withParams_passesDatesAndPaging() throws Exception {
        when(ownerActivityLogService.getOwnerActivityLogs(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get(URL)
                        .param("startDate", "2026-09-01T00:00:00Z")
                        .param("endDate", "2026-10-01T00:00:00Z")
                        .param("page", "2")
                        .param("size", "100")
                        .with(token("ROLE_OWNER")))
                .andExpect(status().isOk());

        verify(ownerActivityLogService).getOwnerActivityLogs(eq(ownerId),
                eq(Instant.parse("2026-09-01T00:00:00Z")), eq(Instant.parse("2026-10-01T00:00:00Z")),
                eq(PageRequest.of(2, 100)));
    }
}
