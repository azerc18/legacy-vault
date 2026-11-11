package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.paymentdto.PlanUsageResponse;
import com.ltld.app.legacyvault.security.SecurityConfig;
import com.ltld.app.legacyvault.service.paymentservice.PaymentService;
import com.ltld.app.legacyvault.service.paymentservice.PlanUsageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Việc 2 (review #253 m1): GET /api/subscriptions/me/usage chỉ cho Owner và chỉ đọc theo id trong token. */
@WebMvcTest(SubscriptionController.class)
@Import(SecurityConfig.class)
class SubscriptionUsageControllerSecurityTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private PaymentService paymentService;
    @MockitoBean private PlanUsageService planUsageService;
    @MockitoBean private JwtDecoder jwtDecoder;

    @Test
    void usage_anonymous_returns401() throws Exception {
        mockMvc.perform(get("/api/subscriptions/me/usage")).andExpect(status().isUnauthorized());
        verifyNoInteractions(planUsageService);
    }

    @Test
    void usage_nonOwner_returns403() throws Exception {
        mockMvc.perform(get("/api/subscriptions/me/usage")
                        .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_BENEFICIARY"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(planUsageService);
    }

    @Test
    void usage_owner_returnsOwnUsage() throws Exception {
        UUID me = UUID.randomUUID();
        when(planUsageService.getUsage(me)).thenReturn(PlanUsageResponse.builder()
                .planCode("FREE").vaultsUsed(1).maxVaults(1).overLimit(List.of()).build());

        mockMvc.perform(get("/api/subscriptions/me/usage")
                        .with(jwt().jwt(j -> j.subject(me.toString())).authorities(new SimpleGrantedAuthority("ROLE_OWNER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.planCode").value("FREE"))
                .andExpect(jsonPath("$.data.vaultsUsed").value(1));
        verify(planUsageService).getUsage(me);
    }
}
