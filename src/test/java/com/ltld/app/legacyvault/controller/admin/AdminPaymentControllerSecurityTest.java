package com.ltld.app.legacyvault.controller.admin;

import com.ltld.app.legacyvault.controller.AdminPaymentController;
import com.ltld.app.legacyvault.dto.adminoverviewdto.AdminPaymentFilter;
import com.ltld.app.legacyvault.dto.adminoverviewdto.AdminPaymentResponse;
import com.ltld.app.legacyvault.dto.adminoverviewdto.AdminPaymentSummaryResponse;
import com.ltld.app.legacyvault.dto.paymentdto.PaymentReceiptData;
import com.ltld.app.legacyvault.enums.PaymentStatus;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.security.SecurityConfig;
import com.ltld.app.legacyvault.service.adminoverviewservice.AdminPaymentService;
import com.ltld.app.legacyvault.service.paymentservice.PaymentReceiptPdfService;
import com.ltld.app.legacyvault.service.paymentservice.PaymentReceiptService;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminPaymentController.class)
@Import(SecurityConfig.class)
class AdminPaymentControllerSecurityTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private AdminPaymentService adminPaymentService;
    @MockitoBean private PaymentReceiptService paymentReceiptService;
    @MockitoBean private PaymentReceiptPdfService paymentReceiptPdfService;
    @MockitoBean private JwtDecoder jwtDecoder;

    private static RequestPostProcessor token(String... authorities) {
        SimpleGrantedAuthority[] granted = Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new).toArray(SimpleGrantedAuthority[]::new);
        return jwt().jwt(j -> j.subject(UUID.randomUUID().toString())).authorities(granted);
    }

    private static RequestPostProcessor admin() {
        return token("ROLE_ADMIN", "ADMIN_MANAGE");
    }

    @Test
    void list_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/payments")).andExpect(status().isUnauthorized());
        verifyNoInteractions(adminPaymentService);
    }

    @Test
    void list_owner_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/payments").with(token("ROLE_OWNER", "VAULT_READ")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(adminPaymentService);
    }

    @Test
    void receipt_owner_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/payments/{id}/receipt", UUID.randomUUID()).with(token("ROLE_OWNER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(paymentReceiptService);
    }

    @Test
    void list_admin_passesFilters() throws Exception {
        when(adminPaymentService.search(any(), anyInt(), anyInt())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/admin/payments").with(admin())
                        .param("status", "SUCCESS").param("email", "a@b.vn")
                        .param("from", "2026-11-01").param("to", "2026-11-08"))
                .andExpect(status().isOk());

        verify(adminPaymentService).search(new AdminPaymentFilter(PaymentStatus.SUCCESS, "a@b.vn",
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 8)), 0, 20);
    }

    @Test
    void list_invalidStatus_returns400() throws Exception {
        mockMvc.perform(get("/api/admin/payments").param("status", "NOPE").with(admin()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void summary_admin_returnsRevenue() throws Exception {
        when(adminPaymentService.summary(any())).thenReturn(AdminPaymentSummaryResponse.builder()
                .totalRevenueVnd(398_000).successCount(2).build());

        mockMvc.perform(get("/api/admin/payments/summary").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRevenueVnd").value(398000))
                .andExpect(jsonPath("$.data.successCount").value(2));
    }

    @Test
    void export_admin_returnsCsv() throws Exception {
        when(adminPaymentService.export(any())).thenReturn(List.of(AdminPaymentResponse.builder()
                .id(UUID.randomUUID()).userEmail("a@b.vn").status(PaymentStatus.SUCCESS).amountVnd(99_000)
                .provider("VNPAY").currentPlanCode("VIP_MONTH").build()));

        mockMvc.perform(get("/api/admin/payments/export").with(admin()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(content().string(containsString("a@b.vn")));
    }

    @Test
    void receipt_admin_returnsPdf() throws Exception {
        UUID id = UUID.randomUUID();
        PaymentReceiptData data = new PaymentReceiptData("LV-20261108-ABCDEF12", id, "A", "a@b.vn", "VIP_MONTH",
                "VIP tháng", 30, 99_000, "123", "NCB", LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(),
                1L, "VNPay");
        when(paymentReceiptService.getReceiptDataForAdmin(id)).thenReturn(data);
        when(paymentReceiptPdfService.render(data)).thenReturn(new byte[]{'%', 'P', 'D', 'F'});

        mockMvc.perform(get("/api/admin/payments/{id}/receipt", id).with(admin()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", containsString("LV-20261108-ABCDEF12")));
    }

    @Test
    void receipt_pendingPayment_returns409() throws Exception {
        when(paymentReceiptService.getReceiptDataForAdmin(any()))
                .thenThrow(new BusinessException(ErrorCode.CONFLICT, "A receipt is only available for successful payments."));

        mockMvc.perform(get("/api/admin/payments/{id}/receipt", UUID.randomUUID()).with(admin()))
                .andExpect(status().isConflict());
    }
}
