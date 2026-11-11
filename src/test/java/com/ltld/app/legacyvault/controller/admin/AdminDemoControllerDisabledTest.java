package com.ltld.app.legacyvault.controller.admin;

import com.ltld.app.legacyvault.controller.AdminDemoController;
import com.ltld.app.legacyvault.security.SecurityConfig;
import com.ltld.app.legacyvault.service.dmsservice.DmsConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Arrays;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Demo tools tắt (mặc định): cờ false và thao tác trả 404, không đụng dịch vụ DMS. */
@WebMvcTest(controllers = AdminDemoController.class)
@Import(SecurityConfig.class)
class AdminDemoControllerDisabledTest {

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

    @Test
    void tools_admin_returnsDisabled() throws Exception {
        mockMvc.perform(get("/api/admin/demo/tools").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dmsSimulateOverdue").value(false));
    }

    @Test
    void simulateOverdue_admin_returns404AndDoesNothing() throws Exception {
        mockMvc.perform(post("/api/admin/demo/dms/{ownerId}/simulate-overdue", OWNER_ID).with(admin()))
                .andExpect(status().isNotFound());
        verifyNoInteractions(dmsConfigService);
    }
}
