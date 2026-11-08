package com.ltld.app.legacyvault.controller.admin;

import com.ltld.app.legacyvault.controller.AdminOverviewController;
import com.ltld.app.legacyvault.dto.adminoverviewdto.AdminStatsResponse;
import com.ltld.app.legacyvault.security.SecurityConfig;
import com.ltld.app.legacyvault.dto.adminoverviewdto.AdminUserDetailResponse;
import com.ltld.app.legacyvault.enums.VaultStatus;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.service.adminoverviewservice.AdminInsightService;
import com.ltld.app.legacyvault.service.adminoverviewservice.AdminStatsService;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
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
    @MockitoBean private AdminInsightService adminInsightService;
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

    @Test
    void userDetail_owner_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/users/{id}", UUID.randomUUID()).with(token("ROLE_OWNER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(adminInsightService);
    }

    @Test
    void userDetail_admin_returnsProfile() throws Exception {
        UUID id = UUID.randomUUID();
        when(adminInsightService.getUserDetail(id)).thenReturn(AdminUserDetailResponse.builder()
                .id(id).email("a@b.vn").roles(List.of("OWNER")).planCode("FREE")
                .vaults(List.of()).recentLogs(List.of()).alerts(List.of()).build());

        mockMvc.perform(get("/api/admin/users/{id}", id).with(token("ROLE_ADMIN", "ADMIN_MANAGE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("a@b.vn"))
                .andExpect(jsonPath("$.data.planCode").value("FREE"));
    }

    @Test
    void userDetail_unknown_returns404() throws Exception {
        when(adminInsightService.getUserDetail(any())).thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "User not found."));
        mockMvc.perform(get("/api/admin/users/{id}", UUID.randomUUID()).with(token("ROLE_ADMIN", "ADMIN_MANAGE")))
                .andExpect(status().isNotFound());
    }

    @Test
    void vaults_admin_passesFilters() throws Exception {
        when(adminInsightService.searchVaults(any(), any(), any(), anyInt(), anyInt())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/admin/vaults").with(token("ROLE_ADMIN", "ADMIN_MANAGE"))
                        .param("q", "nguyen").param("status", "UNLOCKED").param("expiringWithinDays", "7"))
                .andExpect(status().isOk());

        verify(adminInsightService).searchVaults("nguyen", VaultStatus.UNLOCKED, 7, 0, 20);
    }

    @Test
    void vaults_owner_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/vaults").with(token("ROLE_OWNER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(adminInsightService);
    }

    @Test
    void charts_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/stats/charts")).andExpect(status().isUnauthorized());
    }
}
