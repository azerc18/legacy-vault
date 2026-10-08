package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.logindto.LoginRequest;
import com.ltld.app.legacyvault.dto.logindto.LoginResponse;
import com.ltld.app.legacyvault.dto.logindto.LoginResult;
import com.ltld.app.legacyvault.dto.otpdto.SendOtpRequest;
import com.ltld.app.legacyvault.dto.otpdto.VerifyOtpRequest;
import com.ltld.app.legacyvault.dto.registerdto.RegisterRequest;
import com.ltld.app.legacyvault.security.JwtProperties;
import com.ltld.app.legacyvault.service.authservice.AuthService;
import com.ltld.app.legacyvault.service.verificationservice.VerificationTokenService;
import com.ltld.app.legacyvault.utility.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final VerificationTokenService tokenService;
    private final JwtProperties jwtProperties;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> register(@Valid @RequestBody RegisterRequest request) {

        authService.register(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Register Successfully"));
    }

    @PostMapping("/send-otp")
    public ResponseEntity<ApiResponse<Void>> sendOtp(@Valid @RequestBody SendOtpRequest request) {
        tokenService.sendOtp(request);

        return ResponseEntity.ok(ApiResponse.success("OTP was sent"));
    }

    @PostMapping("/verify-email")
        public ResponseEntity<ApiResponse<Void>> verifyEmail(@Valid @RequestBody VerifyOtpRequest request) {
        tokenService.verifyEmail(request);

        return ResponseEntity.ok(ApiResponse.success("Verify Successfully. You can login."));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        LoginResult result = authService.login(
                request,
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader(HttpHeaders.USER_AGENT));

        ResponseCookie refreshCookie = ResponseCookie.from("refresh_token", result.refreshToken())
                .httpOnly(true)
                .secure(jwtProperties.isCookieSecure())
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(jwtProperties.getRefreshTokenTtlSeconds())
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(ApiResponse.success("Login successfully", result.response()));
    }
}
