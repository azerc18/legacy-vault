package com.ltld.app.legacyvault.controller.admin;

import com.ltld.app.legacyvault.controller.AdminOverviewController;
import com.ltld.app.legacyvault.dto.adminoverviewdto.AdminStatsResponse;
import com.ltld.app.legacyvault.security.SecurityConfig;
import com.ltld.app.legacyvault.service.adminoverviewservice.AdminStatsService;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminOverviewController.class)
@Import(SecurityConfig.class)
class AdminOverviewControllerSecurityTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private AdminStatsService adminStatsService;
    @MockitoBean private JwtDecoder jwtDecoder;

    private static RequestPostProcessor token(String... authorities) {
        SimpleGrantedAuthority[] granted = Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new).toArray(SimpleGrantedAuthority[]::new);
        return jwt().jwt(j -> j.subject(UUID.randomUUID().toString())).authorities(granted);
    }

    @Test
    void stats_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/stats")).andExpect(status().isUnauthorized());
        verifyNoInteractions(adminStatsService);
    }

    @Test
    void stats_owner_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/stats").with(token("ROLE_OWNER", "VAULT_READ")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(adminStatsService);
    }

    @Test
    void stats_admin_returnsNumbers() throws Exception {
        when(adminStatsService.getStats()).thenReturn(AdminStatsResponse.builder()
                .usersByStatus(Map.of("ACTIVE", 5L)).vaultsByStatus(Map.of("ACTIVE", 2L))
                .openAlerts(3).revenueThisMonthVnd(199_000).vipUsers(1)
                .highAlerts(List.of()).claimDeadlines(List.of()).stalePendingPayments(List.of())
                .build());

        mockMvc.perform(get("/api/admin/stats").with(token("ROLE_ADMIN", "ADMIN_MANAGE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usersByStatus.ACTIVE").value(5))
                .andExpect(jsonPath("$.data.openAlerts").value(3))
                .andExpect(jsonPath("$.data.revenueThisMonthVnd").value(199000));
    }
}
