package com.ltld.app.legacyvault.controller.admin;

import com.ltld.app.legacyvault.controller.AdminNotificationController;
import com.ltld.app.legacyvault.dto.adminoverviewdto.AdminNotificationResponse;
import com.ltld.app.legacyvault.enums.NotificationStatus;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.security.SecurityConfig;
import com.ltld.app.legacyvault.service.adminoverviewservice.AdminNotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminNotificationController.class)
@Import(SecurityConfig.class)
class AdminNotificationControllerSecurityTest {

    private static final UUID ADMIN_ID = UUID.randomUUID();

    @Autowired private MockMvc mockMvc;
    @MockitoBean private AdminNotificationService adminNotificationService;
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
    void list_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/notifications")).andExpect(status().isUnauthorized());
        verifyNoInteractions(adminNotificationService);
    }

    @Test
    void list_owner_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/notifications").with(token(UUID.randomUUID(), "ROLE_OWNER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(adminNotificationService);
    }

    @Test
    void list_admin_defaultsToFailed() throws Exception {
        when(adminNotificationService.list(any(), anyInt(), anyInt())).thenReturn(Page.empty());
        mockMvc.perform(get("/api/admin/notifications").with(admin())).andExpect(status().isOk());
        verify(adminNotificationService).list(NotificationStatus.FAILED, 0, 20);
    }

    @Test
    void retry_owner_returns403() throws Exception {
        mockMvc.perform(post("/api/admin/notifications/{id}/retry", UUID.randomUUID())
                        .with(token(UUID.randomUUID(), "ROLE_OWNER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(adminNotificationService);
    }

    @Test
    void retry_admin_passesAdminId() throws Exception {
        UUID id = UUID.randomUUID();
        when(adminNotificationService.retry(any(), any())).thenReturn(AdminNotificationResponse.builder().id(id).build());
        mockMvc.perform(post("/api/admin/notifications/{id}/retry", id).with(admin())).andExpect(status().isOk());
        verify(adminNotificationService).retry(ADMIN_ID, id);
    }

    @Test
    void retry_notFailed_returns409() throws Exception {
        when(adminNotificationService.retry(any(), any()))
                .thenThrow(new BusinessException(ErrorCode.CONFLICT, "Only failed emails can be resent."));
        mockMvc.perform(post("/api/admin/notifications/{id}/retry", UUID.randomUUID()).with(admin()))
                .andExpect(status().isConflict());
    }
}
