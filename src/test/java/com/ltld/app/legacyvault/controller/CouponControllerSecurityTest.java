package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.coupondto.AdminCouponResponse;
import com.ltld.app.legacyvault.dto.coupondto.CouponPreviewResponse;
import com.ltld.app.legacyvault.security.SecurityConfig;
import com.ltld.app.legacyvault.service.paymentservice.CouponService;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Việc 4: Owner chỉ xem trước mã; tạo / bật tắt mã cần ADMIN_MANAGE. */
@WebMvcTest({CouponController.class, AdminCouponController.class})
@Import(SecurityConfig.class)
class CouponControllerSecurityTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private CouponService couponService;
    @MockitoBean private JwtDecoder jwtDecoder;

    private static RequestPostProcessor token(UUID subject, String... authorities) {
        SimpleGrantedAuthority[] granted = java.util.Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new).toArray(SimpleGrantedAuthority[]::new);
        return jwt().jwt(j -> j.subject(subject.toString())).authorities(granted);
    }

    @Test
    void preview_owner_usesOwnId() throws Exception {
        UUID me = UUID.randomUUID();
        when(couponService.preview(me, "TET")).thenReturn(new CouponPreviewResponse("TET", 20, null, List.of()));

        mockMvc.perform(get("/api/payments/coupons/preview").param("code", "TET").with(token(me, "ROLE_OWNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.percentOff").value(20));
        verify(couponService).preview(me, "TET");
    }

    @Test
    void preview_anonymous_returns401() throws Exception {
        mockMvc.perform(get("/api/payments/coupons/preview").param("code", "TET")).andExpect(status().isUnauthorized());
        verifyNoInteractions(couponService);
    }

    @Test
    void adminCreate_owner_returns403() throws Exception {
        mockMvc.perform(post("/api/admin/coupons").with(token(UUID.randomUUID(), "ROLE_OWNER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"TET\",\"percentOff\":20}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(couponService);
    }

    @Test
    void adminCreate_percentOver90_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/coupons").with(token(UUID.randomUUID(), "ROLE_ADMIN", "ADMIN_MANAGE"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"TET\",\"percentOff\":100}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(couponService);
    }

    @Test
    void adminCreate_admin_ok() throws Exception {
        UUID admin = UUID.randomUUID();
        when(couponService.create(eq(admin), any())).thenReturn(new AdminCouponResponse(UUID.randomUUID(), "TET", 20,
                null, null, 0, null, true, false, null, null));

        mockMvc.perform(post("/api/admin/coupons").with(token(admin, "ROLE_ADMIN", "ADMIN_MANAGE"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"tet\",\"percentOff\":20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("TET"));
    }
}
