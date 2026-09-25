package com.ltld.app.legacyvault.authtest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ltld.app.legacyvault.controller.AuthController;
import com.ltld.app.legacyvault.dto.otpdto.SendOtpRequest;
import com.ltld.app.legacyvault.dto.otpdto.VerifyOtpRequest;
import com.ltld.app.legacyvault.dto.registerdto.RegisterRequest;
import com.ltld.app.legacyvault.service.authservice.AuthService;
import com.ltld.app.legacyvault.service.verificationservice.VerificationTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
public class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private VerificationTokenService tokenService;

    private RegisterRequest validRegisterRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@example.com");
        request.setFullName("Nguyen Van A");
        request.setPassword("Password123");
        request.setPasswordConfirm("Password123");
        return request;
    }

    @Test
    void register_success_returnsCreated() throws Exception {
        RegisterRequest request = validRegisterRequest();
        doNothing().when(authService).register(any(RegisterRequest.class));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Register Successfully"));

        verify(authService, times(1)).register(any(RegisterRequest.class));
    }

    @Test
    void register_invalidEmail_returnsBadRequest() throws Exception {
        RegisterRequest request = validRegisterRequest();
        request.setEmail("not-an-email");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(authService, never()).register(any());
    }

    @Test
    void register_fullNameTooShort_returnsBadRequest() throws Exception {
        RegisterRequest request = validRegisterRequest();
        request.setFullName("A");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(authService, never()).register(any());
    }

    @Test
    void register_passwordTooShort_returnsBadRequest() throws Exception {
        RegisterRequest request = validRegisterRequest();
        request.setPassword("123");
        request.setPasswordConfirm("123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(authService, never()).register(any());
    }

    @Test
    void register_passwordMismatch_returnsBadRequest() throws Exception {
        RegisterRequest request = validRegisterRequest();
        request.setPasswordConfirm("DifferentPassword123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(authService, never()).register(any());
    }

    @Test
    void sendOtp_success_returnsOk() throws Exception {
        SendOtpRequest request = new SendOtpRequest();
        request.setEmail("test@example.com");

        doNothing().when(tokenService).sendOtp(any(SendOtpRequest.class));

        mockMvc.perform(post("/api/auth/send-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("OTP was sent"));

        verify(tokenService, times(1)).sendOtp(any(SendOtpRequest.class));
    }

    @Test
    void sendOtp_invalidEmail_returnsBadRequest() throws Exception {
        SendOtpRequest request = new SendOtpRequest();
        request.setEmail("not-an-email");

        mockMvc.perform(post("/api/auth/send-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(tokenService, never()).sendOtp(any());
    }

    @Test
    void verifyEmail_success_returnsOk() throws Exception {
        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setEmail("test@example.com");
        request.setOtp("123456");

        doNothing().when(tokenService).verifyEmail(any(VerifyOtpRequest.class));

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Verify Successfully. You can login."));

        verify(tokenService, times(1)).verifyEmail(any(VerifyOtpRequest.class));
    }

    @Test
    void verifyEmail_invalidOtpPattern_returnsBadRequest() throws Exception {
        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setEmail("test@example.com");
        request.setOtp("12a45");

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(tokenService, never()).verifyEmail(any());
    }

    @Test
    void verifyEmail_missingOtp_returnsBadRequest() throws Exception {
        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setEmail("test@example.com");

        mockMvc.perform(post("/api/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(tokenService, never()).verifyEmail(any());
    }
}
