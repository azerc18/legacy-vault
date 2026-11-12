package com.ltld.app.legacyvault.security.ratelimit;

import com.ltld.app.legacyvault.controller.AuthController;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.security.JwtProperties;
import com.ltld.app.legacyvault.security.SecurityConfig;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.authservice.AuthService;
import com.ltld.app.legacyvault.service.verificationservice.VerificationTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.net.URI;
import java.time.Clock;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Filter thật được đăng ký qua RateLimitConfig, chạy trước Spring Security.
 * Mỗi test dùng IP riêng (qua X-Forwarded-For) nên các test không ăn lượt của nhau.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtProperties.class, RateLimitConfig.class, AuthRateLimitFilterTest.ClockTestConfig.class})
@TestPropertySource(properties = {
        "app.rate-limit.enabled=true",
        "app.rate-limit.trust-forwarded-for=true",
        "app.rate-limit.rules.register.limit=2",
        "app.rate-limit.rules.login.limit=3"
})
class AuthRateLimitFilterTest {

    @TestConfiguration
    static class ClockTestConfig {
        @Bean Clock clock() { return Clock.systemUTC(); }
    }

    private static final String REGISTER_BODY = """
            {"fullName":"Nguyen Van A","email":"a@example.com","password":"Password1!","passwordConfirm":"Password1!"}""";
    private static final String LOGIN_BODY = """
            {"email":"a@example.com","password":"wrong"}""";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private AuthService authService;
    @MockitoBean private VerificationTokenService tokenService;
    @MockitoBean private AuditLogService auditLogService;
    @MockitoBean private JwtDecoder jwtDecoder;

    private ResultActions postFrom(String ip, String url, String body) throws Exception {
        return mockMvc.perform(post(url).header("X-Forwarded-For", "6.6.6.6, " + ip + ":5000")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void overLimit_returns429WithRetryAfter_andSkipsController() throws Exception {
        String ip = "203.0.113.1";
        postFrom(ip, "/api/auth/login", LOGIN_BODY);
        postFrom(ip, "/api/auth/login", LOGIN_BODY);
        postFrom(ip, "/api/auth/login/2fa", "{}");    // cùng quy tắc login

        postFrom(ip, "/api/auth/login", LOGIN_BODY)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("IP_RATE_LIMITED"))
                .andExpect(jsonPath("$.data.retryAfterSeconds").isNumber());

        verify(authService, times(2)).login(any(), any(), any());
        verify(auditLogService, times(1)).failure(eq(AuditAction.IP_RATE_LIMITED), isNull(), isNull(),
                contains("ip=" + ip));
    }

    @Test
    void auditWrittenOnce_notOnEveryBlockedRequest() throws Exception {
        String ip = "203.0.113.2";
        for (int i = 0; i < 6; i++) postFrom(ip, "/api/auth/register", REGISTER_BODY);
        verify(auditLogService, times(1)).failure(eq(AuditAction.IP_RATE_LIMITED), isNull(), isNull(), any());
    }

    @Test
    void anotherIp_isNotBlocked() throws Exception {
        for (int i = 0; i < 3; i++) postFrom("203.0.113.3", "/api/auth/register", REGISTER_BODY);
        postFrom("203.0.113.4", "/api/auth/register", REGISTER_BODY).andExpect(status().isCreated());
    }

    @Test
    void getAndUnlistedPaths_areNotLimited() throws Exception {
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/api/auth/google/config").header("X-Forwarded-For", "203.0.113.5"))
                    .andExpect(status().isOk());
        }
        verifyNoInteractions(auditLogService);
    }

    @Test
    void encodedPath_cannotBypassLimit() throws Exception {
        // %72 = "r": Spring giải mã rồi vẫn đưa vào /register, nên filter cũng phải so trên đường dẫn đã giải mã
        String ip = "203.0.113.6";
        for (int i = 0; i < 2; i++) postFrom(ip, "/api/auth/register", REGISTER_BODY);
        mockMvc.perform(post(URI.create("/api/auth/%72egister")).header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON).content(REGISTER_BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("IP_RATE_LIMITED"));
    }
}
