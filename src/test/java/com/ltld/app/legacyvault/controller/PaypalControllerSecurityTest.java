package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.security.SecurityConfig;
import com.ltld.app.legacyvault.service.paymentservice.PaymentReceiptPdfService;
import com.ltld.app.legacyvault.service.paymentservice.PaymentReceiptService;
import com.ltld.app.legacyvault.service.paymentservice.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
@Import(SecurityConfig.class)
class PaypalControllerSecurityTest {

    private static final String RESULT = "https://legacyvault.codes/payment/result?status=success";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private PaymentService paymentService;
    @MockitoBean private PaymentReceiptService paymentReceiptService;
    @MockitoBean private PaymentReceiptPdfService paymentReceiptPdfService;
    @MockitoBean private JwtDecoder jwtDecoder; // SecurityConfig cần có JwtDecoder

    @Test
    void paypalReturn_isPublic_redirectsToFrontend() throws Exception {
        when(paymentService.handlePaypalReturn("ORDER1")).thenReturn(RESULT);

        mockMvc.perform(get("/api/payments/paypal-return").param("token", "ORDER1"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", RESULT));
    }

    @Test
    void paypalCancel_isPublic_redirectsToFrontend() throws Exception {
        when(paymentService.handlePaypalCancel("ORDER1")).thenReturn(RESULT);

        mockMvc.perform(get("/api/payments/paypal-cancel").param("token", "ORDER1"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", RESULT));
    }

    @Test
    void providers_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/payments/providers")).andExpect(status().isUnauthorized());
        verifyNoInteractions(paymentService);
    }

    @Test
    void providers_nonOwner_returns403() throws Exception {
        mockMvc.perform(get("/api/payments/providers")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_BENEFICIARY"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(paymentService);
    }

    @Test
    void providers_owner_getsList() throws Exception {
        when(paymentService.getAvailableProviders()).thenReturn(List.of("VNPAY", "PAYPAL"));

        mockMvc.perform(get("/api/payments/providers")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OWNER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[1]").value("PAYPAL"));
    }
}
