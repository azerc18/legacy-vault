package com.ltld.app.legacyvault.controller.admin;

import com.ltld.app.legacyvault.controller.AdminBillingController;
import com.ltld.app.legacyvault.dto.adminoverviewdto.VipGrantResponse;
import com.ltld.app.legacyvault.security.SecurityConfig;
import com.ltld.app.legacyvault.service.adminoverviewservice.AdminBillingService;
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

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminBillingController.class)
@Import(SecurityConfig.class)
class AdminBillingControllerSecurityTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private AdminBillingService adminBillingService;
    @MockitoBean private JwtDecoder jwtDecoder;

    private static RequestPostProcessor token(String... authorities) {
        SimpleGrantedAuthority[] granted = java.util.Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new).toArray(SimpleGrantedAuthority[]::new);
        return jwt().jwt(j -> j.subject(UUID.randomUUID().toString())).authorities(granted);
    }

    @Test
    void grant_owner_returns403() throws Exception {
        mockMvc.perform(post("/api/admin/users/{id}/vip-grant", UUID.randomUUID()).with(token("ROLE_OWNER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"days\":7,\"reason\":\"x\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(adminBillingService);
    }

    @Test
    void refund_owner_returns403() throws Exception {
        mockMvc.perform(post("/api/admin/payments/{id}/refund", UUID.randomUUID()).with(token("ROLE_OWNER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(adminBillingService);
    }

    @Test
    void grant_admin_validDays_returnsNewExpiry() throws Exception {
        UUID userId = UUID.randomUUID();
        when(adminBillingService.grantVip(any(), eq(userId), any()))
                .thenReturn(new VipGrantResponse("VIP_MONTH", LocalDateTime.of(2026, 12, 1, 0, 0)));

        mockMvc.perform(post("/api/admin/users/{id}/vip-grant", userId).with(token("ROLE_ADMIN", "ADMIN_MANAGE"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"days\":20,\"reason\":\"Bù lỗi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.planCode").value("VIP_MONTH"));
        verify(adminBillingService).grantVip(any(), eq(userId), any());
    }

    @Test
    void grant_admin_tooManyDays_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/users/{id}/vip-grant", UUID.randomUUID()).with(token("ROLE_ADMIN", "ADMIN_MANAGE"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"days\":999,\"reason\":\"x\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(adminBillingService);
    }

    @Test
    void refund_admin_missingReason_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/payments/{id}/refund", UUID.randomUUID()).with(token("ROLE_ADMIN", "ADMIN_MANAGE"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"viaGateway\":true}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(adminBillingService);
    }
}
