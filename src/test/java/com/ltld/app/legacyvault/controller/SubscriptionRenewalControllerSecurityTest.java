package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.paymentdto.SubscriptionMeResponse;
import com.ltld.app.legacyvault.security.SecurityConfig;
import com.ltld.app.legacyvault.service.paymentservice.PaymentService;
import com.ltld.app.legacyvault.service.paymentservice.SubscriptionRenewalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Việc 6: chỉ Owner tự hủy / bật lại gia hạn của chính mình. */
@WebMvcTest(SubscriptionController.class)
@Import(SecurityConfig.class)
class SubscriptionRenewalControllerSecurityTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private PaymentService paymentService;
    @MockitoBean private SubscriptionRenewalService renewalService;
    @MockitoBean private com.ltld.app.legacyvault.service.paymentservice.PlanUsageService planUsageService;
    @MockitoBean private JwtDecoder jwtDecoder;

    @Test
    void cancel_owner_usesOwnIdFromToken() throws Exception {
        UUID me = UUID.randomUUID();
        when(renewalService.cancelAtPeriodEnd(me))
                .thenReturn(SubscriptionMeResponse.builder().planCode("VIP_MONTH").cancelAtPeriodEnd(true).build());

        mockMvc.perform(post("/api/subscriptions/me/cancel")
                        .with(jwt().jwt(j -> j.subject(me.toString())).authorities(new SimpleGrantedAuthority("ROLE_OWNER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cancelAtPeriodEnd").value(true));
        verify(renewalService).cancelAtPeriodEnd(me);
    }

    @Test
    void resume_admin_returns403() throws Exception {
        mockMvc.perform(post("/api/subscriptions/me/resume")
                        .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(renewalService);
    }

    @Test
    void cancel_anonymous_returns401() throws Exception {
        mockMvc.perform(post("/api/subscriptions/me/cancel")).andExpect(status().isUnauthorized());
        verifyNoInteractions(renewalService);
    }
}
