package com.ltld.app.legacyvault.controller.admin;

import com.ltld.app.legacyvault.controller.AdminController;
import com.ltld.app.legacyvault.dto.adminuserdto.AdminUserResponse;
import com.ltld.app.legacyvault.dto.adminuserdto.UpdateUserStatusRequest;
import com.ltld.app.legacyvault.dto.dmsdto.DmsConfigResponse;
import com.ltld.app.legacyvault.enums.DmsStatus;
import com.ltld.app.legacyvault.exception.AdminException;
import com.ltld.app.legacyvault.exception.DmsException;
import com.ltld.app.legacyvault.security.SecurityConfig;
import com.ltld.app.legacyvault.service.adminuserservice.AdminRoleService;
import com.ltld.app.legacyvault.service.adminuserservice.AdminUserService;
import com.ltld.app.legacyvault.service.dmsservice.DmsConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Arrays;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
@Import(SecurityConfig.class)

class AdminControllerSecurityTest {

    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @Autowired private MockMvc mockMvc;
    @MockitoBean private AdminRoleService adminRoleService;
    @MockitoBean private AdminUserService adminUserService;
    @MockitoBean private DmsConfigService dmsConfigService;
    @MockitoBean private JwtDecoder jwtDecoder; // SecurityConfig cần có JwtDecoder

    private static RequestPostProcessor token(UUID subject, String... authorities) {
        SimpleGrantedAuthority[] granted = Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new).toArray(SimpleGrantedAuthority[]::new);
        return jwt().jwt(j -> j.subject(subject.toString())).authorities(granted);
    }

    private static RequestPostProcessor admin() {
        return token(ADMIN_ID, "ROLE_ADMIN", "ADMIN_MANAGE");
    }

    private static RequestPostProcessor owner() {
        return token(UUID.randomUUID(), "ROLE_OWNER", "VAULT_CREATE", "VAULT_READ", "EXECUTOR_MANAGE");
    }


    @Test
    void listUsers_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(adminUserService);
    }

    @Test
    void listUsers_ownerWithoutAdminManage_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/users").with(owner()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(adminUserService);
    }

    @Test
    void grantRole_verifierWithoutAdminManage_returns403() throws Exception {
        mockMvc.perform(post("/api/admin/users/{id}/roles/{code}", USER_ID, "LEGAL_VERIFIER")
                        .with(token(UUID.randomUUID(), "ROLE_LEGAL_VERIFIER", "LEGAL_REVIEW", "VAULT_READ")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(adminUserService);
    }

    @Test
    void suspendDms_ownerWithoutAdminManage_returns403() throws Exception {
        mockMvc.perform(put("/api/admin/dms/{ownerId}/suspend", USER_ID).with(owner()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(dmsConfigService);
    }


    @Test
    void grantRole_admin_returns200_andPassesAdminIdFromJwt() throws Exception {
        when(adminUserService.grantRole(any(), any(), any())).thenReturn(AdminUserResponse.builder().build());

        mockMvc.perform(post("/api/admin/users/{id}/roles/{code}", USER_ID, "LEGAL_VERIFIER").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(adminUserService).grantRole(ADMIN_ID, USER_ID, "LEGAL_VERIFIER");
    }

    @Test
    void grantRole_serviceRejectsRole_returns400() throws Exception {
        when(adminUserService.grantRole(any(), any(), any()))
                .thenThrow(new AdminException("Chỉ quản lý được role LEGAL_VERIFIER và ADMIN", HttpStatus.BAD_REQUEST));

        mockMvc.perform(post("/api/admin/users/{id}/roles/{code}", USER_ID, "OWNER").with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void revokeRole_selfAdmin_returns409() throws Exception {
        when(adminUserService.revokeRole(any(), any(), any()))
                .thenThrow(new AdminException("You cannot remove your own ADMIN role", HttpStatus.CONFLICT));

        mockMvc.perform(delete("/api/admin/users/{id}/roles/{code}", ADMIN_ID, "ADMIN").with(admin()))
                .andExpect(status().isConflict());

        verify(adminUserService).revokeRole(ADMIN_ID, ADMIN_ID, "ADMIN");
    }


    @Test
    void listUsers_admin_defaultParams() throws Exception {
        when(adminUserService.listUsers(any(), any(), any(), anyInt(), anyInt())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/admin/users").with(admin()))
                .andExpect(status().isOk());

        verify(adminUserService).listUsers(isNull(), isNull(), isNull(), eq(0), eq(20));
    }

    @Test
    void listUsers_admin_passesFilters() throws Exception {
        when(adminUserService.listUsers(any(), any(), any(), anyInt(), anyInt())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/admin/users")
                        .param("q", "abc").param("role", "LEGAL_VERIFIER").param("status", "ACTIVE")
                        .param("page", "1").param("size", "50")
                        .with(admin()))
                .andExpect(status().isOk());

        verify(adminUserService).listUsers("abc", "LEGAL_VERIFIER", "ACTIVE", 1, 50);
    }

    @Test
    void listUsers_invalidStatus_returns400() throws Exception {
        when(adminUserService.listUsers(any(), any(), any(), anyInt(), anyInt()))
                .thenThrow(new AdminException("Invalid status", HttpStatus.BAD_REQUEST));

        mockMvc.perform(get("/api/admin/users").param("status", "DELETED").with(admin()))
                .andExpect(status().isBadRequest());
    }


    @Test
    void updateStatus_missingStatus_returns400_withoutCallingService() throws Exception {
        mockMvc.perform(patch("/api/admin/users/{id}/status", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON).content("{}")
                        .with(admin()))
                .andExpect(status().isBadRequest());
        verify(adminUserService, never()).updateStatus(any(), any(), any());
    }

    @Test
    void updateStatus_reasonTooLong_returns400() throws Exception {
        String body = "{\"status\":\"LOCKED\",\"reason\":\"" + "x".repeat(256) + "\"}";
        mockMvc.perform(patch("/api/admin/users/{id}/status", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(admin()))
                .andExpect(status().isBadRequest());
        verify(adminUserService, never()).updateStatus(any(), any(), any());
    }

    @Test
    void updateStatus_admin_returns200() throws Exception {
        when(adminUserService.updateStatus(any(), any(), any())).thenReturn(AdminUserResponse.builder().build());

        mockMvc.perform(patch("/api/admin/users/{id}/status", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"LOCKED\",\"reason\":\"spam\"}")
                        .with(admin()))
                .andExpect(status().isOk());

        verify(adminUserService).updateStatus(eq(ADMIN_ID), eq(USER_ID),
                argThat((UpdateUserStatusRequest r) -> "LOCKED".equals(r.getStatus()) && "spam".equals(r.getReason())));
    }

    @Test
    void updateStatus_noToken_returns401() throws Exception {
        mockMvc.perform(patch("/api/admin/users/{id}/status", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"LOCKED\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(adminUserService);
    }


    @Test
    void suspendDms_noBody_passesNullReason() throws Exception {
        when(dmsConfigService.suspendByAdmin(any(), any(), any()))
                .thenReturn(DmsConfigResponse.builder().status(DmsStatus.SUSPENDED).build());

        mockMvc.perform(put("/api/admin/dms/{ownerId}/suspend", USER_ID).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUSPENDED"));

        verify(dmsConfigService).suspendByAdmin(USER_ID, ADMIN_ID, null);
    }

    @Test
    void suspendDms_withReason_passesReason() throws Exception {
        when(dmsConfigService.suspendByAdmin(any(), any(), any()))
                .thenReturn(DmsConfigResponse.builder().status(DmsStatus.SUSPENDED).build());

        mockMvc.perform(put("/api/admin/dms/{ownerId}/suspend", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"điều tra gian lận\"}")
                        .with(admin()))
                .andExpect(status().isOk());

        verify(dmsConfigService).suspendByAdmin(USER_ID, ADMIN_ID, "điều tra gian lận");
    }

    @Test
    void suspendDms_reasonTooLong_returns400() throws Exception {
        mockMvc.perform(put("/api/admin/dms/{ownerId}/suspend", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"" + "x".repeat(256) + "\"}")
                        .with(admin()))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(dmsConfigService);
    }

    @Test
    void suspendDms_alreadyTriggered_returns409() throws Exception {
        when(dmsConfigService.suspendByAdmin(any(), any(), any()))
                .thenThrow(new DmsException("DMS đã kích hoạt", HttpStatus.CONFLICT));

        mockMvc.perform(put("/api/admin/dms/{ownerId}/suspend", USER_ID).with(admin()))
                .andExpect(status().isConflict());
    }

    @Test
    void suspendDms_noConfig_returns404() throws Exception {
        when(dmsConfigService.suspendByAdmin(any(), any(), any()))
                .thenThrow(new DmsException("The Dead Man's Switch has not been configured.", HttpStatus.NOT_FOUND));

        mockMvc.perform(put("/api/admin/dms/{ownerId}/suspend", USER_ID).with(admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void resumeDms_admin_returns200() throws Exception {
        when(dmsConfigService.resumeByAdmin(any(), any()))
                .thenReturn(DmsConfigResponse.builder().status(DmsStatus.ACTIVE).build());

        mockMvc.perform(put("/api/admin/dms/{ownerId}/resume", USER_ID).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        verify(dmsConfigService).resumeByAdmin(USER_ID, ADMIN_ID);
    }

    @Test
    void resumeDms_notSuspended_returns409() throws Exception {
        when(dmsConfigService.resumeByAdmin(any(), any()))
                .thenThrow(new DmsException("DMS không ở trạng thái SUSPENDED", HttpStatus.CONFLICT));

        mockMvc.perform(put("/api/admin/dms/{ownerId}/resume", USER_ID).with(admin()))
                .andExpect(status().isConflict());
    }
}