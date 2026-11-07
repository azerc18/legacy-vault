package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.paymentdto.PaymentReceiptData;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.security.SecurityConfig;
import com.ltld.app.legacyvault.service.paymentservice.PaymentReceiptPdfService;
import com.ltld.app.legacyvault.service.paymentservice.PaymentReceiptService;
import com.ltld.app.legacyvault.service.paymentservice.PaymentService;
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

import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** T2: quyền và header của GET /api/payments/{id}/receipt. */
@WebMvcTest(PaymentController.class)
@Import(SecurityConfig.class)
class PaymentReceiptControllerSecurityTest {

    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID TX_ID = UUID.randomUUID();
    private static final String URL = "/api/payments/" + TX_ID + "/receipt";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private PaymentService paymentService;
    @MockitoBean private PaymentReceiptService paymentReceiptService;
    @MockitoBean private PaymentReceiptPdfService paymentReceiptPdfService;
    @MockitoBean private JwtDecoder jwtDecoder; // SecurityConfig cần có JwtDecoder

    private static RequestPostProcessor token(String... authorities) {
        SimpleGrantedAuthority[] granted = java.util.Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new).toArray(SimpleGrantedAuthority[]::new);
        return jwt().jwt(j -> j.subject(OWNER_ID.toString())).authorities(granted);
    }

    private static PaymentReceiptData data() {
        LocalDateTime paid = LocalDateTime.of(2026, 11, 7, 10, 0);
        return new PaymentReceiptData("LV-20261107-1A2B3C4D", TX_ID, "Owner", "owner@example.com",
                "VIP_MONTH", "VIP Monthly", 30, 99_000L, "14512345", "NCB", paid, paid, paid);
    }

    @Test
    void noToken_returns401() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        verifyNoInteractions(paymentReceiptService, paymentReceiptPdfService);
    }

    @Test
    void nonOwnerRole_returns403() throws Exception {
        mockMvc.perform(get(URL).with(token("ROLE_BENEFICIARY")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(paymentReceiptService, paymentReceiptPdfService);
    }

    @Test
    void owner_getsPdfAttachment_withSafeHeaders() throws Exception {
        when(paymentReceiptService.getReceiptData(TX_ID, OWNER_ID)).thenReturn(data());
        when(paymentReceiptPdfService.render(any())).thenReturn("%PDF-1.4 test".getBytes());

        mockMvc.perform(get(URL).with(token("ROLE_OWNER")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition",
                        containsString("bien-lai-LV-20261107-1A2B3C4D.pdf")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    @Test
    void notOwnTransaction_returns404() throws Exception {
        when(paymentReceiptService.getReceiptData(TX_ID, OWNER_ID))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "Payment not found."));

        mockMvc.perform(get(URL).with(token("ROLE_OWNER")))
                .andExpect(status().isNotFound());
        verifyNoInteractions(paymentReceiptPdfService);
    }

    @Test
    void unpaidTransaction_returns409() throws Exception {
        when(paymentReceiptService.getReceiptData(TX_ID, OWNER_ID))
                .thenThrow(new BusinessException(ErrorCode.CONFLICT, "A receipt is only available for successful payments."));

        mockMvc.perform(get(URL).with(token("ROLE_OWNER")))
                .andExpect(status().isConflict());
        verifyNoInteractions(paymentReceiptPdfService);
    }
}
