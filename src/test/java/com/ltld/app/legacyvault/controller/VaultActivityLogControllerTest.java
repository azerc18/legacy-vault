package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.security.SecurityConfig;
import com.ltld.app.legacyvault.service.auditservice.VaultActivityLogService;
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
import java.time.temporal.ChronoUnit;
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

@WebMvcTest(VaultActivityLogController.class)
@Import(SecurityConfig.class)
class VaultActivityLogControllerTest {

    private static final String URL = "/api/vaults/{vaultId}/activity-logs";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private VaultActivityLogService vaultActivityLogService;
    @MockitoBean private JwtDecoder jwtDecoder;

    private final UUID vaultId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();

    private RequestPostProcessor ownerToken() {
        return jwt().jwt(j -> j.subject(ownerId.toString()))
                .authorities(new SimpleGrantedAuthority("VAULT_READ"));
    }

    @Test
    void getVaultLogs_sizeAbove100_returns400() throws Exception {
        mockMvc.perform(get(URL, vaultId).param("size", "101").with(ownerToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verifyNoInteractions(vaultActivityLogService);
    }

    @Test
    void getVaultLogs_sizeZero_returns400() throws Exception {
        mockMvc.perform(get(URL, vaultId).param("size", "0").with(ownerToken()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(vaultActivityLogService);
    }

    @Test
    void getVaultLogs_negativePage_returns400() throws Exception {
        mockMvc.perform(get(URL, vaultId).param("page", "-1").with(ownerToken()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(vaultActivityLogService);
    }

    @Test
    void getVaultLogs_size100_passesPageRequestToService() throws Exception {
        when(vaultActivityLogService.getVaultActivityLogs(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get(URL, vaultId).param("page", "2").param("size", "100").with(ownerToken()))
                .andExpect(status().isOk());

        verify(vaultActivityLogService).getVaultActivityLogs(
                eq(vaultId), eq(ownerId), isNull(), isNull(), eq(PageRequest.of(2, 100)));
    }

    @Test
    void getVaultLogs_rangeOver365Days_delegatesValidationToService() throws Exception {
        Instant endDate = Instant.parse("2026-10-01T00:00:00Z");
        Instant startDate = endDate.minus(400, ChronoUnit.DAYS);
        when(vaultActivityLogService.getVaultActivityLogs(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get(URL, vaultId)
                        .param("startDate", startDate.toString())
                        .param("endDate", endDate.toString())
                        .with(ownerToken()))
                .andExpect(status().isOk());

        verify(vaultActivityLogService).getVaultActivityLogs(
                eq(vaultId), eq(ownerId), eq(startDate), eq(endDate), eq(PageRequest.of(0, 20)));
    }
}
