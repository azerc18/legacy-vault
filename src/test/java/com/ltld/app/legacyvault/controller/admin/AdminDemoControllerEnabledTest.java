package com.ltld.app.legacyvault.controller.admin;

import com.ltld.app.legacyvault.controller.AdminDemoController;
import com.ltld.app.legacyvault.dto.dmsdto.DmsConfigResponse;
import com.ltld.app.legacyvault.enums.DmsStatus;
import com.ltld.app.legacyvault.exception.DmsException;
import com.ltld.app.legacyvault.security.SecurityConfig;
import com.ltld.app.legacyvault.service.dmsservice.DmsConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Arrays;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Demo tools bật: Admin thấy cờ true và giả được Owner quá hạn; người không phải Admin bị 403. */
@WebMvcTest(controllers = AdminDemoController.class, properties = "app.demo.tools-enabled=true")
@Import(SecurityConfig.class)
class AdminDemoControllerEnabledTest {

    static final UUID ADMIN_ID = UUID.randomUUID();
    static final UUID OWNER_ID = UUID.randomUUID();

    @Autowired private MockMvc mockMvc;
    @MockitoBean private DmsConfigService dmsConfigService;
    @MockitoBean private JwtDecoder jwtDecoder;

    static RequestPostProcessor token(UUID subject, String... authorities) {
        SimpleGrantedAuthority[] granted = Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new).toArray(SimpleGrantedAuthority[]::new);
        return jwt().jwt(j -> j.subject(subject.toString())).authorities(granted);
    }

    static RequestPostProcessor admin() {
        return token(ADMIN_ID, "ROLE_ADMIN", "ADMIN_MANAGE");
    }

    static RequestPostProcessor owner() {
        return token(OWNER_ID, "ROLE_OWNER", "VAULT_CREATE", "VAULT_READ", "EXECUTOR_MANAGE");
    }

    @Test
    void tools_admin_returnsEnabled() throws Exception {
        mockMvc.perform(get("/api/admin/demo/tools").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dmsSimulateOverdue").value(true));
    }

    @Test
    void simulateOverdue_admin_callsServiceWithAdminIdFromJwt() throws Exception {
        when(dmsConfigService.simulateOverdueByAdmin(any(), any()))
                .thenReturn(DmsConfigResponse.builder().status(DmsStatus.ACTIVE).build());

        mockMvc.perform(post("/api/admin/demo/dms/{ownerId}/simulate-overdue", OWNER_ID).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        verify(dmsConfigService).simulateOverdueByAdmin(OWNER_ID, ADMIN_ID);
    }

    @Test
    void simulateOverdue_notActive_returns409() throws Exception {
        when(dmsConfigService.simulateOverdueByAdmin(any(), any()))
                .thenThrow(new DmsException("Only an active Dead Man's Switch can be made overdue.", HttpStatus.CONFLICT));

        mockMvc.perform(post("/api/admin/demo/dms/{ownerId}/simulate-overdue", OWNER_ID).with(admin()))
                .andExpect(status().isConflict());
    }

    @Test
    void simulateOverdue_owner_returns403() throws Exception {
        mockMvc.perform(post("/api/admin/demo/dms/{ownerId}/simulate-overdue", OWNER_ID).with(owner()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(dmsConfigService);
    }

    @Test
    void tools_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/demo/tools"))
                .andExpect(status().isUnauthorized());
    }
}
