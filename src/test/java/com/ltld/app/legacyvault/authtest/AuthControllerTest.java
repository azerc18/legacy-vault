package com.ltld.app.legacyvault.authtest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ltld.app.legacyvault.controller.AuthController;
import com.ltld.app.legacyvault.dto.logindto.LoginRequest;
import com.ltld.app.legacyvault.dto.logindto.LoginResponse;
import com.ltld.app.legacyvault.dto.logindto.LoginResult;
import com.ltld.app.legacyvault.dto.otpdto.SendOtpRequest;
import com.ltld.app.legacyvault.dto.otpdto.VerifyOtpRequest;
import com.ltld.app.legacyvault.dto.registerdto.RegisterRequest;
import com.ltld.app.legacyvault.exception.InvalidCredentialException;
import com.ltld.app.legacyvault.exception.LockedAccountException;
import com.ltld.app.legacyvault.exception.NotActiveUserException;
import com.ltld.app.legacyvault.security.JwtProperties;
import com.ltld.app.legacyvault.service.authservice.AuthService;
import com.ltld.app.legacyvault.service.verificationservice.VerificationTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(JwtProperties.class)
public class AuthControllerTest {

    private static final String REGISTER_URL = "/api/auth/register";
    private static final String SEND_OTP_URL = "/api/auth/send-otp";
    private static final String VERIFY_EMAIL_URL = "/api/auth/verify-email";
    private static final String LOGIN_URL = "/api/auth/login";

    private static final String EMAIL = "test@example.com";
    private static final String IP = "127.0.0.1";          // remoteAddr mặc định của MockMvc
    private static final String USER_AGENT = "JUnit";
    private static final Instant EXPIRES_AT = Instant.parse("2026-10-01T04:36:20Z");

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private VerificationTokenService tokenService;

    // ---------- helpers dùng chung ----------

    private ResultActions postJson(String url, Object body) throws Exception {
        return mockMvc.perform(post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private RegisterRequest validRegisterRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail(EMAIL);
        request.setFullName("Nguyen Van A");
        request.setPassword("Password123");
        request.setPasswordConfirm("Password123");
        return request;
    }

    private SendOtpRequest sendOtpRequest(String email) {
        SendOtpRequest request = new SendOtpRequest();
        request.setEmail(email);
        return request;
    }

    private VerifyOtpRequest verifyOtpRequest(String email, String otp) {
        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setEmail(email);
        request.setOtp(otp);
        return request;
    }

    private LoginRequest validLoginRequest() {
        LoginRequest request = new LoginRequest();
        request.setEmail(EMAIL);
        request.setPassword("Password123");
        return request;
    }

    private LoginResult loginResult() {


        LoginResponse response = LoginResponse.builder()
                .accessToken("access-token")
                .tokenType("Bearer")
                .expiresAt(EXPIRES_AT)
                .build();

        return new LoginResult(response, "raw-refresh-token");
    }

    // ---------- POST /api/auth/register ----------

    @Nested
    @DisplayName("POST /api/auth/register")
    class Register {

        @Test
        void register_success_returnsCreated() throws Exception {
            doNothing().when(authService).register(any(RegisterRequest.class));

            postJson(REGISTER_URL, validRegisterRequest())
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.message").value("Register Successfully"));

            verify(authService, times(1)).register(any(RegisterRequest.class));
        }

        @Test
        void register_invalidEmail_returnsBadRequest() throws Exception {
            RegisterRequest request = validRegisterRequest();
            request.setEmail("not-an-email");

            postJson(REGISTER_URL, request).andExpect(status().isBadRequest());

            verify(authService, never()).register(any());
        }

        @Test
        void register_fullNameTooShort_returnsBadRequest() throws Exception {
            RegisterRequest request = validRegisterRequest();
            request.setFullName("A");

            postJson(REGISTER_URL, request).andExpect(status().isBadRequest());

            verify(authService, never()).register(any());
        }

        @Test
        void register_passwordTooShort_returnsBadRequest() throws Exception {
            RegisterRequest request = validRegisterRequest();
            request.setPassword("123");
            request.setPasswordConfirm("123");

            postJson(REGISTER_URL, request).andExpect(status().isBadRequest());

            verify(authService, never()).register(any());
        }

        @Test
        void register_passwordMismatch_returnsBadRequest() throws Exception {
            RegisterRequest request = validRegisterRequest();
            request.setPasswordConfirm("DifferentPassword123");

            postJson(REGISTER_URL, request).andExpect(status().isBadRequest());

            verify(authService, never()).register(any());
        }
    }

    // ---------- POST /api/auth/send-otp ----------

    @Nested
    @DisplayName("POST /api/auth/send-otp")
    class SendOtp {

        @Test
        void sendOtp_success_returnsOk() throws Exception {
            doNothing().when(tokenService).sendOtp(any(SendOtpRequest.class));

            postJson(SEND_OTP_URL, sendOtpRequest(EMAIL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("OTP was sent"));

            verify(tokenService, times(1)).sendOtp(any(SendOtpRequest.class));
        }

        @Test
        void sendOtp_invalidEmail_returnsBadRequest() throws Exception {
            postJson(SEND_OTP_URL, sendOtpRequest("not-an-email"))
                    .andExpect(status().isBadRequest());

            verify(tokenService, never()).sendOtp(any());
        }
    }

    // ---------- POST /api/auth/verify-email ----------

    @Nested
    @DisplayName("POST /api/auth/verify-email")
    class VerifyEmail {

        @Test
        void verifyEmail_success_returnsOk() throws Exception {
            doNothing().when(tokenService).verifyEmail(any(VerifyOtpRequest.class));

            postJson(VERIFY_EMAIL_URL, verifyOtpRequest(EMAIL, "123456"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Verify Successfully. You can login."));

            verify(tokenService, times(1)).verifyEmail(any(VerifyOtpRequest.class));
        }

        @Test
        void verifyEmail_invalidOtpPattern_returnsBadRequest() throws Exception {
            postJson(VERIFY_EMAIL_URL, verifyOtpRequest(EMAIL, "12a45"))
                    .andExpect(status().isBadRequest());

            verify(tokenService, never()).verifyEmail(any());
        }

        @Test
        void verifyEmail_missingOtp_returnsBadRequest() throws Exception {
            postJson(VERIFY_EMAIL_URL, verifyOtpRequest(EMAIL, null))
                    .andExpect(status().isBadRequest());

            verify(tokenService, never()).verifyEmail(any());
        }
    }

    // ---------- POST /api/auth/login ----------

    @Nested
    @DisplayName("POST /api/auth/login")
    class Login {

        @Test
        void login_success_returnsOkWithAccessTokenInBody() throws Exception {
            when(authService.login(any(LoginRequest.class), any(), any())).thenReturn(loginResult());

            postJson(LOGIN_URL, validLoginRequest())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                    .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.data.expiresAt").value("2026-10-01T04:36:20Z"));
        }

        @Test
        void login_success_setsRefreshTokenInHttpOnlyCookieNotInBody() throws Exception {
            when(authService.login(any(LoginRequest.class), any(), any())).thenReturn(loginResult());

            postJson(LOGIN_URL, validLoginRequest())
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refresh_token=raw-refresh-token")))
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/auth")))
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")))
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=604800")))
                    .andExpect(jsonPath("$.data.refreshToken").doesNotExist());
        }

        @Test
        void login_passesClientIpAndUserAgentToService() throws Exception {
            when(authService.login(any(LoginRequest.class), any(), any())).thenReturn(loginResult());

            mockMvc.perform(post(LOGIN_URL)
                            .header(HttpHeaders.USER_AGENT, USER_AGENT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validLoginRequest())))
                    .andExpect(status().isOk());

            verify(authService).login(any(LoginRequest.class), eq(IP), eq(USER_AGENT));
        }

        @Test
        void login_invalidCredential_returnsUnauthorizedJson() throws Exception {
            when(authService.login(any(LoginRequest.class), any(), any()))
                    .thenThrow(new InvalidCredentialException());

            postJson(LOGIN_URL, validLoginRequest())
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        void login_lockedAccount_returnsForbiddenJson() throws Exception {
            when(authService.login(any(LoginRequest.class), any(), any()))
                    .thenThrow(new LockedAccountException());

            postJson(LOGIN_URL, validLoginRequest())
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        void login_notActiveUser_returnsForbiddenJson() throws Exception {
            when(authService.login(any(LoginRequest.class), any(), any()))
                    .thenThrow(new NotActiveUserException());

            postJson(LOGIN_URL, validLoginRequest())
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        void login_blankEmail_returnsBadRequest() throws Exception {
            LoginRequest request = validLoginRequest();
            request.setEmail("");

            postJson(LOGIN_URL, request)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.data.email").exists());

            verifyNoInteractions(authService);
        }

        @Test
        void login_blankPassword_returnsBadRequest() throws Exception {
            LoginRequest request = validLoginRequest();
            request.setPassword("");

            postJson(LOGIN_URL, request)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.data.password").exists());

            verifyNoInteractions(authService);
        }
    }


}