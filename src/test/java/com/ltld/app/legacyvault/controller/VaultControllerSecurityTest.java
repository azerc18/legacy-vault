package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.vaultdto.AssignBeneficiaryResponse;
import com.ltld.app.legacyvault.security.SecurityConfig;
import com.ltld.app.legacyvault.service.vaultservice.VaultService;
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

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VaultController.class)
@Import(SecurityConfig.class)
class VaultControllerSecurityTest {

    private static final String BODY = "{\"email\":\"ben@example.com\"}";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private VaultService vaultService;
    @MockitoBean private JwtDecoder jwtDecoder; // SecurityConfig cần có JwtDecoder

    private static RequestPostProcessor token(String... authorities) {
        SimpleGrantedAuthority[] granted = java.util.Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new).toArray(SimpleGrantedAuthority[]::new);
        return jwt().jwt(j -> j.subject(UUID.randomUUID().toString())).authorities(granted);
    }

    @Test
    void assignBeneficiary_noToken_returns401() throws Exception {
        mockMvc.perform(put("/api/vaults/{v}/beneficiary", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void assignBeneficiary_beneficiaryOnly_returns403() throws Exception {
        mockMvc.perform(put("/api/vaults/{v}/beneficiary", UUID.randomUUID())
                        .with(token("ROLE_BENEFICIARY"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden());
        verify(vaultService, never()).assignBeneficiary(any(), any(), any());
    }

    @Test
    void assignBeneficiary_owner_returns200() throws Exception {
        when(vaultService.assignBeneficiary(any(), any(), any()))
                .thenReturn(AssignBeneficiaryResponse.builder().build());

        mockMvc.perform(put("/api/vaults/{v}/beneficiary", UUID.randomUUID())
                        .with(token("ROLE_OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk());
    }
}
