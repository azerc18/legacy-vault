package com.ltld.app.legacyvault.controller.admin;

import com.ltld.app.legacyvault.controller.AdminIntegrationsController;
import com.ltld.app.legacyvault.dto.integrationdto.IntegrationsResponse;
import com.ltld.app.legacyvault.dto.integrationdto.UpdateKycMockRequest;
import com.ltld.app.legacyvault.security.SecurityConfig;
import com.ltld.app.legacyvault.service.integrationservice.IntegrationSettingsService;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Trang Tích hợp: chỉ ADMIN_MANAGE; đổi cờ eKYC mô phỏng bắt buộc có lý do. */
@WebMvcTest(AdminIntegrationsController.class)
@Import(SecurityConfig.class)
class AdminIntegrationsControllerSecurityTest {

    private static final UUID ADMIN_ID = UUID.randomUUID();

    @Autowired private MockMvc mockMvc;
    @MockitoBean private IntegrationSettingsService integrationSettingsService;
    @MockitoBean private JwtDecoder jwtDecoder;

    private static RequestPostProcessor token(UUID subject, String... authorities) {
        SimpleGrantedAuthority[] granted = Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new).toArray(SimpleGrantedAuthority[]::new);
        return jwt().jwt(j -> j.subject(subject.toString())).authorities(granted);
    }

    private static RequestPostProcessor admin() {
        return token(ADMIN_ID, "ROLE_ADMIN", "ADMIN_MANAGE");
    }

    private static IntegrationsResponse response(boolean mock) {
        return new IntegrationsResponse(
                new IntegrationsResponse.Kyc(mock, mock ? Boolean.TRUE : null, false, true, null, null),
                new IntegrationsResponse.Ai("gemini", true, false, true, false, false),
                new IntegrationsResponse.Signature("EMAIL_OTP", false),
                new IntegrationsResponse.Payments(new IntegrationsResponse.Gateway(true, true),
                        new IntegrationsResponse.Gateway(false, true), true),
                new IntegrationsResponse.Mail("smtp.gmail.com", true),
                new IntegrationsResponse.Captcha(false, false, false),
                new IntegrationsResponse.GoogleLogin(false));
    }

    @Test
    void get_anonymous_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/integrations")).andExpect(status().isUnauthorized());
        verifyNoInteractions(integrationSettingsService);
    }

    @Test
    void get_owner_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/integrations").with(token(UUID.randomUUID(), "ROLE_OWNER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(integrationSettingsService);
    }

    @Test
    void get_admin_returnsStatusAndNoStore() throws Exception {
        when(integrationSettingsService.get()).thenReturn(response(false));

        mockMvc.perform(get("/api/admin/integrations").with(admin()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.kyc.mockEnabled").value(false))
                .andExpect(jsonPath("$.data.payments.vnpay.sandbox").value(true))
                .andExpect(jsonPath("$.data.mail.host").value("smtp.gmail.com"));
    }

    @Test
    void putKycMock_owner_returns403() throws Exception {
        mockMvc.perform(put("/api/admin/integrations/kyc-mock").with(token(UUID.randomUUID(), "ROLE_OWNER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true,\"reason\":\"Demo hội đồng\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(integrationSettingsService);
    }

    @Test
    void putKycMock_withoutReason_returns400() throws Exception {
        mockMvc.perform(put("/api/admin/integrations/kyc-mock").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true,\"reason\":\" \"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(integrationSettingsService);
    }

    @Test
    void putKycMock_admin_passesChoiceAndReason() throws Exception {
        when(integrationSettingsService.updateKycMock(eq(ADMIN_ID), any())).thenReturn(response(true));

        mockMvc.perform(put("/api/admin/integrations/kyc-mock").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true,\"reason\":\"Demo hội đồng\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kyc.mockEnabled").value(true));
        verify(integrationSettingsService).updateKycMock(ADMIN_ID, new UpdateKycMockRequest(true, "Demo hội đồng"));
    }

    @Test
    void putKycMock_nullEnabled_meansFollowEnv() throws Exception {
        when(integrationSettingsService.updateKycMock(eq(ADMIN_ID), any())).thenReturn(response(false));

        mockMvc.perform(put("/api/admin/integrations/kyc-mock").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":null,\"reason\":\"Hết buổi demo\"}"))
                .andExpect(status().isOk());
        verify(integrationSettingsService).updateKycMock(ADMIN_ID, new UpdateKycMockRequest(null, "Hết buổi demo"));
    }
}
