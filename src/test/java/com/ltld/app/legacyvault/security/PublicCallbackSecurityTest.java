package com.ltld.app.legacyvault.security;

import com.ltld.app.legacyvault.controller.VaultController;
import com.ltld.app.legacyvault.service.vaultservice.VaultService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Callback VNPay và link trong email được gọi không kèm JWT nên phải qua được SecurityConfig.
 * X2: VNPay chỉ mở GET; link DMS trong email chỉ mở POST, vì máy quét link tự mở GET và không được đổi trạng thái.
 * Chỉ nạp VaultController: các controller callback không có trong context, nên request đã qua lớp bảo mật
 * sẽ rơi vào lỗi của tầng MVC (404/405), không phải 401/403.
 */
@WebMvcTest(VaultController.class)
@Import(SecurityConfig.class)
class PublicCallbackSecurityTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private VaultService vaultService;
    @MockitoBean private JwtDecoder jwtDecoder; // SecurityConfig cần có JwtDecoder

    // Đã qua tầng security: không bị 401 (thiếu token) hay 403 (bị chặn quyền)
    private static ResultMatcher notBlockedBySecurity() {
        return result -> assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
    }

    // ---------- VNPay: chỉ GET ----------

    @ParameterizedTest
    @ValueSource(strings = {"/api/payments/vnpay-ipn", "/api/payments/vnpay-return"})
    void vnpayCallback_getWithoutToken_isNotUnauthorized(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/payments/vnpay-ipn", "/api/payments/vnpay-return"})
    void vnpayCallback_postWithoutToken_stillReturns401(String path) throws Exception {
        // Chỉ mở GET; method khác vẫn phải có JWT.
        mockMvc.perform(post(path))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    // ---------- Link DMS trong email: chỉ POST ----------

    @ParameterizedTest
    @ValueSource(strings = {"/api/dms/checkin-link", "/api/dms/confirm"})
    void dmsEmailLink_getWithoutToken_returns401(String path) throws Exception {
        // X2: GET không còn được permitAll, máy quét link không thể tự check-in / xác nhận
        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/dms/checkin-link", "/api/dms/confirm"})
    void dmsEmailLink_postWithoutToken_isNotBlockedBySecurity(String path) throws Exception {
        mockMvc.perform(post(path))
                .andExpect(notBlockedBySecurity());
    }

    @Test
    void protectedApi_getWithoutToken_stillReturns401() throws Exception {
        mockMvc.perform(get("/api/vaults"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }
}
