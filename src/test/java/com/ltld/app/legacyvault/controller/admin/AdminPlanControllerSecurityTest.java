package com.ltld.app.legacyvault.controller.admin;

import com.ltld.app.legacyvault.controller.AdminPlanController;
import com.ltld.app.legacyvault.dto.adminoverviewdto.AdminPlanResponse;
import com.ltld.app.legacyvault.dto.adminoverviewdto.UpdatePlanPriceRequest;
import com.ltld.app.legacyvault.security.SecurityConfig;
import com.ltld.app.legacyvault.service.adminoverviewservice.AdminPlanService;
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
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminPlanController.class)
@Import(SecurityConfig.class)
class AdminPlanControllerSecurityTest {

    private static final UUID ADMIN_ID = UUID.randomUUID();

    @Autowired private MockMvc mockMvc;
    @MockitoBean private AdminPlanService adminPlanService;
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
    void list_owner_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/plans").with(token(UUID.randomUUID(), "ROLE_OWNER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(adminPlanService);
    }

    @Test
    void list_admin_returnsPlans() throws Exception {
        when(adminPlanService.list()).thenReturn(List.of(AdminPlanResponse.builder().code("VIP_MONTH").priceVnd(99_000).build()));
        mockMvc.perform(get("/api/admin/plans").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("VIP_MONTH"));
    }

    @Test
    void update_owner_returns403() throws Exception {
        mockMvc.perform(patch("/api/admin/plans/VIP_MONTH").with(token(UUID.randomUUID(), "ROLE_OWNER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"priceVnd\":120000}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(adminPlanService);
    }

    @Test
    void update_admin_passesRequest() throws Exception {
        when(adminPlanService.updatePrice(any(), any(), any())).thenReturn(AdminPlanResponse.builder().build());
        mockMvc.perform(patch("/api/admin/plans/VIP_MONTH").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"priceVnd\":120000,\"reason\":\"Khuyến mãi\"}"))
                .andExpect(status().isOk());
        verify(adminPlanService).updatePrice(ADMIN_ID, "VIP_MONTH", new UpdatePlanPriceRequest(120_000L, null, "Khuyến mãi"));
    }

    @Test
    void update_priceTooLow_returns400() throws Exception {
        mockMvc.perform(patch("/api/admin/plans/VIP_MONTH").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"priceVnd\":100}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(adminPlanService);
    }

    @Test
    void update_missingPrice_returns400() throws Exception {
        mockMvc.perform(patch("/api/admin/plans/VIP_MONTH").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(adminPlanService);
    }
}
