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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Callback VNPay và link trong email được gọi không kèm JWT nên phải qua được SecurityConfig.
 * Chỉ nạp VaultController: các controller callback không có trong context, nên request đã qua lớp bảo mật
 * sẽ rơi vào 404 NOT_FOUND (không phải 401).
 */
@WebMvcTest(VaultController.class)
@Import(SecurityConfig.class)
class PublicCallbackSecurityTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private VaultService vaultService;
    @MockitoBean private JwtDecoder jwtDecoder; // SecurityConfig cần có JwtDecoder

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/payments/vnpay-ipn",
            "/api/payments/vnpay-return",
            "/api/dms/checkin-link",
            "/api/dms/confirm"})
    void publicCallback_getWithoutToken_isNotUnauthorized(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/payments/vnpay-ipn",
            "/api/payments/vnpay-return",
            "/api/dms/checkin-link",
            "/api/dms/confirm"})
    void publicCallback_postWithoutToken_stillReturns401(String path) throws Exception {
        // Chỉ mở GET; method khác vẫn phải có JWT.
        mockMvc.perform(post(path))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }

    @Test
    void protectedApi_getWithoutToken_stillReturns401() throws Exception {
        mockMvc.perform(get("/api/vaults"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }
}
