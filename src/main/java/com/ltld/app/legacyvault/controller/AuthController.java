package com.ltld.app.legacyvault.controller;

import com.ltld.app.legacyvault.dto.forgotpassworddto.ForgotPasswordRequest;
import com.ltld.app.legacyvault.dto.forgotpassworddto.ResetPasswordRequest;
import com.ltld.app.legacyvault.dto.logindto.LoginRequest;
import com.ltld.app.legacyvault.dto.logindto.LoginResponse;
import com.ltld.app.legacyvault.dto.logindto.LoginResult;
import com.ltld.app.legacyvault.dto.logindto.MfaLoginRequest;
import com.ltld.app.legacyvault.dto.logindto.CaptchaConfigResponse;
import com.ltld.app.legacyvault.dto.logindto.GoogleConfigResponse;
import com.ltld.app.legacyvault.dto.logindto.GoogleLoginRequest;
import com.ltld.app.legacyvault.dto.otpdto.SendOtpRequest;
import com.ltld.app.legacyvault.dto.otpdto.VerifyOtpRequest;
import com.ltld.app.legacyvault.dto.registerdto.RegisterRequest;
import com.ltld.app.legacyvault.security.JwtProperties;
import com.ltld.app.legacyvault.service.authservice.AuthService;
import com.ltld.app.legacyvault.service.captchaservice.CaptchaService;
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
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final VerificationTokenService tokenService;
    private final JwtProperties jwtProperties;
    private final CaptchaService captchaService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> register(@Valid @RequestBody RegisterRequest request) {
        captchaService.requirePassed(request.getCaptchaToken(), "register");
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
            @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        LoginResult result = authService.login(request,
                httpRequest.getRemoteAddr(), httpRequest.getHeader(HttpHeaders.USER_AGENT));
        if (result.refreshToken() == null) {   // H4: cần bước 2, chưa set cookie
            return ResponseEntity.ok(ApiResponse.success("Two-factor code required", result.response()));
        }
        return withRefreshCookie(result, "Login successfully");
    }

    @PostMapping("/login/2fa")
    public ResponseEntity<ApiResponse<LoginResponse>> loginWithMfa(
            @Valid @RequestBody MfaLoginRequest request, HttpServletRequest httpRequest) {
        LoginResult result = authService.loginWithMfa(request,
                httpRequest.getRemoteAddr(), httpRequest.getHeader(HttpHeaders.USER_AGENT));
        return withRefreshCookie(result, "Login successfully");
    }

    // Đăng nhập bằng Google: FE gửi ID token lấy từ Google Identity Services
    @PostMapping("/google")
    public ResponseEntity<ApiResponse<LoginResponse>> loginWithGoogle(
            @Valid @RequestBody GoogleLoginRequest request, HttpServletRequest httpRequest) {
        LoginResult result = authService.loginWithGoogle(request,
                httpRequest.getRemoteAddr(), httpRequest.getHeader(HttpHeaders.USER_AGENT));
        if (result.refreshToken() == null) {   // tài khoản bật 2FA: vẫn phải nhập mã ở /login/2fa
            return ResponseEntity.ok(ApiResponse.success("Two-factor code required", result.response()));
        }
        return withRefreshCookie(result, "Login successfully");
    }

    // Công khai: FE hỏi có bật CAPTCHA (Turnstile) không và site key là gì
    @GetMapping("/captcha/config")
    public ResponseEntity<ApiResponse<CaptchaConfigResponse>> captchaConfig() {
        return ResponseEntity.ok(ApiResponse.success(new CaptchaConfigResponse(
                captchaService.isEnabled(), captchaService.siteKey(), captchaService.loginAfterFailures())));
    }

    // Công khai: FE hỏi có bật Google không và Client ID là gì
    @GetMapping("/google/config")
    public ResponseEntity<ApiResponse<GoogleConfigResponse>> googleConfig() {
        return ResponseEntity.ok(ApiResponse.success(authService.googleConfig()));
    }

    private ResponseEntity<ApiResponse<LoginResponse>> withRefreshCookie(LoginResult result, String message) {
        ResponseCookie refreshCookie = ResponseCookie.from("refresh_token", result.refreshToken())
                .httpOnly(true)
                .secure(jwtProperties.isCookieSecure())
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(jwtProperties.getRefreshTokenTtlSeconds())
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(ApiResponse.success(message, result.response()));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = "refresh_token", required = false) String refreshToken){
        authService.logout(refreshToken);

        ResponseCookie cookie = ResponseCookie.from("refresh_token","")
                .httpOnly(true)
                .secure(jwtProperties.isCookieSecure())
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(0)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.success("Logout successfully"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            HttpServletRequest httpRequest) {
        LoginResult result = authService.refresh(
                refreshToken,
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader(HttpHeaders.USER_AGENT));

        ResponseCookie cookie = ResponseCookie.from("refresh_token",result.refreshToken())
                .httpOnly(true)
                .secure(jwtProperties.isCookieSecure())
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(jwtProperties.getRefreshTokenTtlSeconds())
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.success("Token refreshed successfully", result.response()));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        captchaService.requirePassed(request.getCaptchaToken(), "forgot-password");
        tokenService.sendPasswordResetOtp(request);
        return ResponseEntity.ok(ApiResponse.success("Reset code has been sent."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success("Password reset successfully. Please login again."));
    }
}
