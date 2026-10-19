package com.ltld.app.legacyvault.controller.dms;

import com.ltld.app.legacyvault.controller.DmsController;
import com.ltld.app.legacyvault.dto.dmsdto.DmsConfigResponse;
import com.ltld.app.legacyvault.security.SecurityConfig;
import com.ltld.app.legacyvault.service.dmsservice.DmsConfigService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DmsController.class)
@Import(SecurityConfig.class)
class DmsControllerSecurityTest {

    private static final String BODY = "{\"checkinIntervalDays\":30,\"gracePeriodDays\":7,\"reminderCount\":3}";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private DmsConfigService dmsConfigService;
    @MockitoBean private JwtDecoder jwtDecoder; // SecurityConfig cần có JwtDecoder

    private static RequestPostProcessor token(String... authorities) {
        SimpleGrantedAuthority[] granted = java.util.Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new).toArray(SimpleGrantedAuthority[]::new);
        return jwt().jwt(j -> j.subject(UUID.randomUUID().toString())).authorities(granted);
    }

    // ---------- GET /api/dms ----------

    @Test
    void getConfig_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/dms"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getConfig_beneficiaryOnly_returns403() throws Exception {
        mockMvc.perform(get("/api/dms").with(token("ROLE_BENEFICIARY")))
                .andExpect(status().isForbidden());
        verify(dmsConfigService, never()).getConfig(any());
    }

    @Test
    void getConfig_owner_returns200() throws Exception {
        when(dmsConfigService.getConfig(any())).thenReturn(DmsConfigResponse.builder().build());

        mockMvc.perform(get("/api/dms").with(token("ROLE_OWNER")))
                .andExpect(status().isOk());
    }

    // ---------- PUT /api/dms ----------

    @Test
    void upsert_noToken_returns401() throws Exception {
        mockMvc.perform(put("/api/dms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void upsert_executorOnly_returns403() throws Exception {
        mockMvc.perform(put("/api/dms")
                        .with(token("ROLE_EXECUTOR", "VAULT_READ"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden());
        verify(dmsConfigService, never()).upsertConfig(any(), any());
    }

    @Test
    void upsert_owner_returns201() throws Exception {
        when(dmsConfigService.upsertConfig(any(), any()))
                .thenReturn(new DmsConfigService.UpsertResult(DmsConfigResponse.builder().build(), true));

        mockMvc.perform(put("/api/dms")
                        .with(token("ROLE_OWNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isCreated());
    }

    // ---------- POST /api/dms/check-in ----------

    @Test
    void checkIn_noToken_returns401() throws Exception {
        mockMvc.perform(post("/api/dms/check-in"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void checkIn_beneficiaryOnly_returns403() throws Exception {
        mockMvc.perform(post("/api/dms/check-in").with(token("ROLE_BENEFICIARY")))
                .andExpect(status().isForbidden());
        verify(dmsConfigService, never()).checkIn(any());
    }

    @Test
    void checkIn_owner_returns200() throws Exception {
        when(dmsConfigService.checkIn(any())).thenReturn(DmsConfigResponse.builder().build());

        mockMvc.perform(post("/api/dms/check-in").with(token("ROLE_OWNER")))
                .andExpect(status().isOk());
    }
}
