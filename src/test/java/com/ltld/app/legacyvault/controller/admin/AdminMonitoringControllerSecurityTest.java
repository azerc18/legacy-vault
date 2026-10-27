package com.ltld.app.legacyvault.controller.admin;

import com.ltld.app.legacyvault.controller.AdminMonitoringController;
import com.ltld.app.legacyvault.dto.auditdto.SecurityAlertResponse;
import com.ltld.app.legacyvault.exception.AdminException;
import com.ltld.app.legacyvault.security.SecurityConfig;
import com.ltld.app.legacyvault.service.auditservice.AdminAuditLogService;
import com.ltld.app.legacyvault.service.auditservice.SecurityAlertService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Arrays;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminMonitoringController.class)
@Import(SecurityConfig.class)
class AdminMonitoringControllerSecurityTest {

    private static final UUID ADMIN_ID = UUID.randomUUID();

    @Autowired private MockMvc mockMvc;
    @MockitoBean private AdminAuditLogService adminAuditLogService;
    @MockitoBean private SecurityAlertService securityAlertService;
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
    void auditLogs_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs")).andExpect(status().isUnauthorized());
        verifyNoInteractions(adminAuditLogService);
    }

    @Test
    void auditLogs_owner_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs").with(token(UUID.randomUUID(), "ROLE_OWNER", "VAULT_READ")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(adminAuditLogService);
    }

    @Test
    void auditLogs_admin_returns200_withDefaults() throws Exception {
        when(adminAuditLogService.search(any(), any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/admin/audit-logs").param("ip", "1.2.3.4").with(admin()))
                .andExpect(status().isOk());

        verify(adminAuditLogService).search(null, null, "1.2.3.4", null, null, 0, 50);
    }

    @Test
    void auditLogs_invalidAction_returns400() throws Exception {
        when(adminAuditLogService.search(any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenThrow(new AdminException("Action không hợp lệ", HttpStatus.BAD_REQUEST));

        mockMvc.perform(get("/api/admin/audit-logs").param("action", "NOPE").with(admin()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void countOpenAlerts_admin_returnsNumber() throws Exception {
        when(securityAlertService.countOpen()).thenReturn(3L);

        mockMvc.perform(get("/api/admin/security-alerts/count").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.open").value(3));
    }

    @Test
    void acknowledge_admin_passesAdminIdFromJwt() throws Exception {
        UUID alertId = UUID.randomUUID();
        when(securityAlertService.acknowledge(any(), any())).thenReturn(SecurityAlertResponse.builder().build());

        mockMvc.perform(patch("/api/admin/security-alerts/{id}/acknowledge", alertId).with(admin()))
                .andExpect(status().isOk());

        verify(securityAlertService).acknowledge(ADMIN_ID, alertId);
    }

    @Test
    void acknowledge_alreadyAcknowledged_returns409() throws Exception {
        when(securityAlertService.acknowledge(any(), any()))
                .thenThrow(new AdminException("The alert has already been acknowledged", HttpStatus.CONFLICT));

        mockMvc.perform(patch("/api/admin/security-alerts/{id}/acknowledge", UUID.randomUUID()).with(admin()))
                .andExpect(status().isConflict());
    }
}