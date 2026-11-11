package com.ltld.app.legacyvault.controller.admin;

import com.ltld.app.legacyvault.controller.AdminRevenueController;
import com.ltld.app.legacyvault.dto.adminoverviewdto.AdminRevenueResponse;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.security.SecurityConfig;
import com.ltld.app.legacyvault.service.adminoverviewservice.AdminRevenueService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminRevenueController.class)
@Import(SecurityConfig.class)
class AdminRevenueControllerSecurityTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private AdminRevenueService adminRevenueService;
    @MockitoBean private JwtDecoder jwtDecoder;

    private static RequestPostProcessor token(String... authorities) {
        SimpleGrantedAuthority[] granted = Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new).toArray(SimpleGrantedAuthority[]::new);
        return jwt().jwt(j -> j.subject(UUID.randomUUID().toString())).authorities(granted);
    }

    private static AdminRevenueResponse.Totals totals(long collected) {
        return new AdminRevenueResponse.Totals(collected, 0, collected, 1, 0, 2, 50.0, collected, 0);
    }

    @Test
    void noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/revenue")).andExpect(status().isUnauthorized());
        verifyNoInteractions(adminRevenueService);
    }

    @Test
    void owner_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/revenue").with(token("ROLE_OWNER", "VAULT_READ")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(adminRevenueService);
    }

    @Test
    void admin_defaultsTo30Days() throws Exception {
        LocalDate today = LocalDate.of(2026, 11, 11);
        when(adminRevenueService.report("30d")).thenReturn(new AdminRevenueResponse("30d", today.minusDays(29), today,
                List.of(new AdminRevenueResponse.Bucket(today, 199_000, 1, 0)), totals(199_000), totals(0),
                List.of(new AdminRevenueResponse.Slice("VIP_MONTH", "VIP tháng", 199_000, 1)), List.of()));

        mockMvc.perform(get("/api/admin/revenue").with(token("ROLE_ADMIN", "ADMIN_MANAGE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.range").value("30d"))
                .andExpect(jsonPath("$.data.buckets[0].collectedVnd").value(199000))
                .andExpect(jsonPath("$.data.current.conversionPercent").value(50.0))
                .andExpect(jsonPath("$.data.byPlan[0].label").value("VIP tháng"));
    }

    @Test
    void admin_unknownRange_returns400() throws Exception {
        when(adminRevenueService.report("7d"))
                .thenThrow(new BusinessException(ErrorCode.INVALID_PARAMETER, "range must be 30d or 12m."));

        mockMvc.perform(get("/api/admin/revenue").param("range", "7d").with(token("ROLE_ADMIN", "ADMIN_MANAGE")))
                .andExpect(status().isBadRequest());
    }
}
