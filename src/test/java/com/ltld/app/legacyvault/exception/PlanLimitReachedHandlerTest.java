package com.ltld.app.legacyvault.exception;

import com.ltld.app.legacyvault.controller.VaultController;
import com.ltld.app.legacyvault.enums.PlanFeature;
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

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** T1: PlanLimitReachedException trả 403 PLAN_LIMIT_REACHED kèm data.feature để FE biết tính năng nào bị chặn. */
@WebMvcTest(VaultController.class)
@Import(SecurityConfig.class)
class PlanLimitReachedHandlerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private VaultService vaultService;
    @MockitoBean private JwtDecoder jwtDecoder; // SecurityConfig cần có JwtDecoder

    @Test
    void planLimitReached_returns403WithFeature() throws Exception {
        when(vaultService.createVault(any(), any()))
                .thenThrow(new PlanLimitReachedException(PlanFeature.MULTIPLE_VAULTS, "Free plan allows only one vault."));

        mockMvc.perform(post("/api/vaults")
                        .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))
                                .authorities(new SimpleGrantedAuthority("VAULT_CREATE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Két thứ hai\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("PLAN_LIMIT_REACHED"))
                .andExpect(jsonPath("$.message").value("Free plan allows only one vault."))
                .andExpect(jsonPath("$.data.feature").value("MULTIPLE_VAULTS"));
    }
}
