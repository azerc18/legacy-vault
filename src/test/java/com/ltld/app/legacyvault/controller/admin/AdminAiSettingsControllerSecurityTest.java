package com.ltld.app.legacyvault.controller.admin;

import com.ltld.app.legacyvault.controller.AdminAiSettingsController;
import com.ltld.app.legacyvault.dto.aisettingsdto.AiFeatureSettingResponse;
import com.ltld.app.legacyvault.dto.aisettingsdto.AiSettingsResponse;
import com.ltld.app.legacyvault.dto.aisettingsdto.UpdateAiFeatureSettingRequest;
import com.ltld.app.legacyvault.enums.AiFeature;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.security.SecurityConfig;
import com.ltld.app.legacyvault.service.aisettingsservice.AiSettingsService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminAiSettingsController.class)
@Import(SecurityConfig.class)
class AdminAiSettingsControllerSecurityTest {

    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final String URL = "/api/admin/ai-settings";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private AiSettingsService aiSettingsService;
    @MockitoBean private JwtDecoder jwtDecoder;

    private static RequestPostProcessor token(UUID subject, String... authorities) {
        SimpleGrantedAuthority[] granted = Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new).toArray(SimpleGrantedAuthority[]::new);
        return jwt().jwt(j -> j.subject(subject.toString())).authorities(granted);
    }

    private static RequestPostProcessor admin() {
        return token(ADMIN_ID, "ROLE_ADMIN", "ADMIN_MANAGE");
    }

    @Test
    void get_noToken_returns401() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        verifyNoInteractions(aiSettingsService);
    }

    @Test
    void get_owner_returns403() throws Exception {
        mockMvc.perform(get(URL).with(token(UUID.randomUUID(), "ROLE_OWNER"))).andExpect(status().isForbidden());
        verifyNoInteractions(aiSettingsService);
    }

    @Test
    void get_admin_returns200_withoutAnyKeyValue() throws Exception {
        when(aiSettingsService.get()).thenReturn(new AiSettingsResponse("gemini", true, false,
                "gemini-3.6-flash", "gemini-3.5-flash", List.of("gemini-3.6-flash", "gemini-3.5-flash"), List.of()));
        mockMvc.perform(get(URL).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.apiKeyConfigured").value(true))
                .andExpect(jsonPath("$.data.apiKey").doesNotExist());
    }

    @Test
    void update_noToken_returns401() throws Exception {
        mockMvc.perform(put(URL + "/ASSISTANT").contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(aiSettingsService);
    }

    @Test
    void update_executor_returns403() throws Exception {
        mockMvc.perform(put(URL + "/ASSISTANT").with(token(UUID.randomUUID(), "ROLE_EXECUTOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(aiSettingsService);
    }

    @Test
    void update_admin_passesAdminIdFeatureAndBody() throws Exception {
        when(aiSettingsService.update(any(), any(), any())).thenReturn(new AiFeatureSettingResponse(
                "ASSISTANT", false, "gemini-3.6-flash", "gemini-3.5-flash", null, null, null, null));
        mockMvc.perform(put(URL + "/ASSISTANT").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false,\"primaryModel\":null,\"backupModel\":\"\"}"))
                .andExpect(status().isOk());
        verify(aiSettingsService).update(eq(ADMIN_ID), eq(AiFeature.ASSISTANT),
                eq(new UpdateAiFeatureSettingRequest(false, null, "")));
    }

    @Test
    void update_unknownFeature_returns400() throws Exception {
        mockMvc.perform(put(URL + "/NOT_A_FEATURE").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(aiSettingsService);
    }

    @Test
    void update_missingEnabled_returns400() throws Exception {
        mockMvc.perform(put(URL + "/ASSISTANT").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(aiSettingsService);
    }

    @Test
    void update_modelNotAllowed_returns400WithCode() throws Exception {
        when(aiSettingsService.update(any(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.AI_MODEL_NOT_ALLOWED, "This model is not in the allowed list."));
        mockMvc.perform(put(URL + "/ASSISTANT").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true,\"primaryModel\":\"gpt-5\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("AI_MODEL_NOT_ALLOWED"));
    }
}
