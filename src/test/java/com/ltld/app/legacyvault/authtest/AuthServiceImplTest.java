package com.ltld.app.legacyvault.authtest;

import com.ltld.app.legacyvault.dto.forgotpassworddto.ResetPasswordRequest;
import com.ltld.app.legacyvault.dto.logindto.LoginRequest;
import com.ltld.app.legacyvault.dto.logindto.LoginResponse;
import com.ltld.app.legacyvault.dto.logindto.LoginResult;
import com.ltld.app.legacyvault.entity.Role;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.dto.registerdto.RegisterRequest;
import com.ltld.app.legacyvault.enums.AppLanguage;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.RevokedReason;
import com.ltld.app.legacyvault.enums.TokenType;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.exception.*;
import com.ltld.app.legacyvault.repository.RoleRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.authservice.AuthServiceImpl;
import com.ltld.app.legacyvault.service.mfaservice.MfaService;
import com.ltld.app.legacyvault.service.tokenservice.TokenService;
import com.ltld.app.legacyvault.service.verificationservice.VerificationTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import com.ltld.app.legacyvault.dto.logindto.MfaLoginRequest;
import com.ltld.app.legacyvault.dto.logindto.GoogleConfigResponse;
import com.ltld.app.legacyvault.dto.logindto.GoogleLoginRequest;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.service.authservice.GoogleTokenVerifier;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
public class AuthServiceImplTest {
    private static final String IP = "127.0.0.1";
    private static final String USER_AGENT = "JUnit";
    private static final Instant EXPIRES_AT = Instant.parse("2026-10-01T04:36:20Z");

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private TokenService tokenService;

    @Mock
    private VerificationTokenService verificationTokenService;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private AuthServiceImpl authService;

    @Mock
    MfaService mfaService;

    @Mock
    GoogleTokenVerifier googleTokenVerifier;


    private Role ownerRole;
    private Role beneficiaryRole;
    private User user;

    private ResetPasswordRequest resetRequest(String otp) {
        return new ResetPasswordRequest("test@example.com", otp, "NewPass123", "NewPass123");
    }

    @BeforeEach
    void setUp() {
        ownerRole = Role.builder().code("OWNER").build();
        beneficiaryRole = Role.builder().code("BENEFICIARY").build();
        user = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .fullName("Nguyen Van A")
                .passwordHash("hashed-password")
                .status(UserStatus.ACTIVE)
                .failedLoginAttempts(0)
                .roles(Set.of(ownerRole))
                .build();
    }

    private RegisterRequest validRegisterRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@example.com");
        request.setFullName("Nguyen Van A");
        request.setPassword("Password123");
        request.setPasswordConfirm("Password123");
        return request;
    }

    private LoginRequest loginRequest(String email, String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }

    @Test
    void register_success_savesUser_issuesOtp_andLogs() {
        RegisterRequest request = validRegisterRequest();

        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(roleRepository.findByCode("OWNER")).thenReturn(Optional.of(ownerRole));
        when(roleRepository.findByCode("BENEFICIARY")).thenReturn(Optional.of(beneficiaryRole));
        when(passwordEncoder.encode(request.getPassword())).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        authService.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getEmail()).isEqualTo("test@example.com");
        assertThat(savedUser.getFullName()).isEqualTo(request.getFullName());
        assertThat(savedUser.getPasswordHash()).isEqualTo("hashed-password");
        assertThat(savedUser.getRoles()).containsExactlyInAnyOrder(ownerRole, beneficiaryRole);

        verify(verificationTokenService).issueOtp(savedUser, TokenType.EMAIL_VERIFICATION);
        verify(auditLogService).success(AuditAction.REGISTER, savedUser.getId(), "test@example.com");
    }

    @Test
    void register_emailWithSpacesAndUpperCase_isNormalizedBeforeSave() {
        RegisterRequest request = validRegisterRequest();
        request.setEmail("  Test@Example.COM ");

        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(roleRepository.findByCode("OWNER")).thenReturn(Optional.of(ownerRole));
        when(roleRepository.findByCode("BENEFICIARY")).thenReturn(Optional.of(beneficiaryRole));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("test@example.com");
    }

    @Test
    void register_savesRequestedLanguage_andDefaultsToVietnamese() {
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(roleRepository.findByCode("OWNER")).thenReturn(Optional.of(ownerRole));
        when(roleRepository.findByCode("BENEFICIARY")).thenReturn(Optional.of(beneficiaryRole));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        RegisterRequest english = validRegisterRequest();
        english.setLanguage("en");
        authService.register(english);
        RegisterRequest unspecified = validRegisterRequest();
        authService.register(unspecified);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getPreferredLanguage()).isEqualTo(AppLanguage.EN);
        assertThat(captor.getAllValues().get(1).getPreferredLanguage()).isEqualTo(AppLanguage.VI);
    }

    @Test
    void register_emailAlreadyExists_throws_logsFailure_andDoesNotSave() {
        RegisterRequest request = validRegisterRequest();
        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(EmailAlreadyExistsException.class)
                .hasMessage("Email already exists.");

        verify(auditLogService).failure(AuditAction.REGISTER, null,
                "test@example.com", "email already exists");
        verify(userRepository, never()).save(any());
        verify(roleRepository, never()).findByCode(anyString());
        verify(passwordEncoder, never()).encode(anyString());
        verifyNoInteractions(verificationTokenService);
    }

    @Test
    void register_defaultRoleMissing_throwsRuntimeException_andDoesNotSave() {
        RegisterRequest request = validRegisterRequest();
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(roleRepository.findByCode("OWNER")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Default role not found in database.");

        verify(userRepository, never()).save(any());
        verifyNoInteractions(verificationTokenService);
    }

    @Test
    void login_success_returnsTokensAndResetsFailedAttempts() {
        user.setFailedLoginAttempts(3);
        LoginRequest request = loginRequest("test@example.com", "Password123");

        when(userRepository.findByEmailForUpdate("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123", "hashed-password")).thenReturn(true);
        when(tokenService.generateAccessToken(user)).thenReturn("access-token");
        when(tokenService.generateRefreshToken(user, IP, USER_AGENT)).thenReturn("refresh-token");
        when(tokenService.getAccessTokenExpiry()).thenReturn(EXPIRES_AT);

        LoginResult result = authService.login(request, IP, USER_AGENT);

        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        assertThat(result.response().getAccessToken()).isEqualTo("access-token");
        assertThat(result.response().getTokenType()).isEqualTo("Bearer");
        assertThat(result.response().getExpiresAt()).isEqualTo(EXPIRES_AT);

        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLastLoginAt()).isNotNull();
        verify(userRepository).save(user);
        verify(auditLogService).success(AuditAction.LOGIN, user.getId(), "test@example.com");

    }

    @Test
    void login_emailWithSpacesAndUpperCase_isNormalizedBeforeLookup() {
        LoginRequest request = loginRequest("  Test@Example.COM ", "Password123");

        when(userRepository.findByEmailForUpdate("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123", "hashed-password")).thenReturn(true);
        when(tokenService.generateAccessToken(user)).thenReturn("access-token");
        when(tokenService.generateRefreshToken(user, IP, USER_AGENT)).thenReturn("refresh-token");
        when(tokenService.getAccessTokenExpiry()).thenReturn(EXPIRES_AT);

        authService.login(request, IP, USER_AGENT);

        verify(userRepository).findByEmailForUpdate("test@example.com");
    }

    @Test
    void login_unknownEmail_throwsInvalidCredential() {
        LoginRequest request = loginRequest("nobody@example.com", "Password123");
        when(userRepository.findByEmailForUpdate("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request, IP, USER_AGENT))
                .isInstanceOf(InvalidCredentialException.class);

        verify(passwordEncoder).matches(eq(request.getPassword()), anyString());
        verifyNoInteractions(tokenService);
        verify(userRepository, never()).save(any());
        verify(auditLogService).failure(AuditAction.LOGIN_FAILED, null,
                "nobody@example.com", "unknown email");
    }

    @Test
    void login_lockedAccount_throwsLockedWithoutCheckingPassword() {
        user.setStatus(UserStatus.LOCKED);
        LoginRequest request = loginRequest("test@example.com", "Password123");
        when(userRepository.findByEmailForUpdate("test@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(request, IP, USER_AGENT))
                .isInstanceOf(LockedAccountException.class);

        verifyNoInteractions(passwordEncoder, tokenService);
        verify(userRepository, never()).save(any());
        verify(auditLogService).failure(AuditAction.LOGIN_FAILED, user.getId(),
                "test@example.com", "account locked");
    }

    @Test
    void login_wrongPassword_incrementsFailedAttemptsAndThrows() {
        LoginRequest request = loginRequest("test@example.com", "WrongPassword");
        when(userRepository.findByEmailForUpdate("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPassword", "hashed-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request, IP, USER_AGENT))
                .isInstanceOf(InvalidCredentialException.class);

        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(userRepository).save(user);
        verifyNoInteractions(tokenService);
        verify(auditLogService).failure(AuditAction.LOGIN_FAILED, user.getId(),
                "test@example.com", "wrong password, attempt 1");
    }

    @Test
    void login_fifthWrongPassword_locksTemporarily() {
        user.setFailedLoginAttempts(4);
        LoginRequest request = loginRequest("test@example.com", "WrongPassword");
        when(userRepository.findByEmailForUpdate("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPassword", "hashed-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request, IP, USER_AGENT))
                .isInstanceOf(LockedAccountException.class);

        assertThat(user.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getLockedUntil())
                .isAfter(Instant.now().plus(14, ChronoUnit.MINUTES))
                .isBefore(Instant.now().plus(16, ChronoUnit.MINUTES));
        verify(userRepository).save(user);
        verifyNoInteractions(tokenService);
        verify(auditLogService).failure(eq(AuditAction.ACCOUNT_LOCKED), eq(user.getId()),
                eq("test@example.com"), contains("after 5 failed attempts"));
    }

    @Test
    void login_temporarilyLocked_throwsLockedWithoutCheckingPassword() {
        user.setLockedUntil(Instant.now().plus(10, ChronoUnit.MINUTES));
        LoginRequest request = loginRequest("test@example.com", "Password123");
        when(userRepository.findByEmailForUpdate("test@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(request, IP, USER_AGENT))
                .isInstanceOf(LockedAccountException.class);

        verifyNoInteractions(passwordEncoder, tokenService);
        verify(userRepository, never()).save(any());
        verify(auditLogService).failure(eq(AuditAction.LOGIN_FAILED), eq(user.getId()),
                eq("test@example.com"), contains("temporarily locked"));
    }

    @Test
    void login_lockExpired_resetsStateAndAllowsLogin() {
        user.setFailedLoginAttempts(5);
        user.setLockedUntil(Instant.now().minus(1, ChronoUnit.MINUTES));
        LoginRequest request = loginRequest("test@example.com", "Password123");

        when(userRepository.findByEmailForUpdate("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123", "hashed-password")).thenReturn(true);
        when(tokenService.generateAccessToken(user)).thenReturn("access-token");
        when(tokenService.generateRefreshToken(user, IP, USER_AGENT)).thenReturn("refresh-token");
        when(tokenService.getAccessTokenExpiry()).thenReturn(EXPIRES_AT);

        LoginResult result = authService.login(request, IP, USER_AGENT);

        assertThat(result.response().getAccessToken()).isEqualTo("access-token");
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.getFailedLoginAttempts()).isZero();
    }

    @Test
    void login_lockExpired_thenWrongPassword_countsFromOneAgain() {
        user.setFailedLoginAttempts(5);
        user.setLockedUntil(Instant.now().minus(1, ChronoUnit.MINUTES));
        LoginRequest request = loginRequest("test@example.com", "WrongPassword");

        when(userRepository.findByEmailForUpdate("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPassword", "hashed-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request, IP, USER_AGENT))
                .isInstanceOf(InvalidCredentialException.class);

        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(user.getLockedUntil()).isNull();
    }

    @Test
    void login_pendingUserWithCorrectPassword_throwsNotActive() {
        user.setStatus(UserStatus.PENDING);
        LoginRequest request = loginRequest("test@example.com", "Password123");
        when(userRepository.findByEmailForUpdate("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123", "hashed-password")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(request, IP, USER_AGENT))
                .isInstanceOf(NotActiveUserException.class);

        verifyNoInteractions(tokenService);
        verify(auditLogService).failure(AuditAction.LOGIN_FAILED, user.getId(),
                "test@example.com", "email not verified");
    }

    @Test
    void logout_withToken_revokesRefreshTokenWithLogoutReason() {
        authService.logout("raw-refresh-token");

        verify(tokenService).revokeRefreshToken("raw-refresh-token", RevokedReason.LOGOUT);
        verifyNoMoreInteractions(tokenService);
        verifyNoInteractions(auditLogService);
    }

    @Test
    void logout_withNullToken_delegatesWithoutThrowing() {
        authService.logout(null);

        verify(tokenService).revokeRefreshToken(null, RevokedReason.LOGOUT);
    }

    @Test
    void refresh_delegatesToTokenService() {
        LoginResponse response = LoginResponse.builder().accessToken("new-access").build();
        LoginResult expectedResult = new LoginResult(response, "new-refresh");

        when(tokenService.refreshAccessToken("raw-token", IP, USER_AGENT)).thenReturn(expectedResult);

        LoginResult result = authService.refresh("raw-token", IP, USER_AGENT);

        assertThat(result).isEqualTo(expectedResult);
        verify(tokenService).refreshAccessToken("raw-token", IP, USER_AGENT);
    }

    @Test
    void resetPassword_success_updatesPassword_unlocks_andRevokesTokens() {
        user.setFailedLoginAttempts(3);
        user.setLockedUntil(Instant.now().plusSeconds(600));
        when(userRepository.findByEmailForUpdate("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("NewPass123")).thenReturn("new-hash");

        authService.resetPassword(resetRequest("123456"));

        verify(verificationTokenService).consumeOtp(user, TokenType.PASSWORD_RESET,
                "123456", AuditAction.PASSWORD_RESET_FAILED);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getPasswordHash()).isEqualTo("new-hash");
        assertThat(saved.getFailedLoginAttempts()).isZero();
        assertThat(saved.getLockedUntil()).isNull();

        verify(tokenService).revokeAllUserTokens(user.getId(), RevokedReason.PASSWORD_CHANGED);
        verify(auditLogService).success(AuditAction.PASSWORD_RESET_SUCCESS, user.getId(), "test@example.com");
    }

    @Test
    void resetPassword_wrongOtp_doesNotChangePasswordOrRevoke() {
        when(userRepository.findByEmailForUpdate("test@example.com")).thenReturn(Optional.of(user));
        doThrow(new InvalidOtpException("Wrong OTP for user"))
                .when(verificationTokenService)
                .consumeOtp(user, TokenType.PASSWORD_RESET, "000000", AuditAction.PASSWORD_RESET_FAILED);

        assertThatThrownBy(() -> authService.resetPassword(resetRequest("000000")))
                .isInstanceOf(InvalidOtpException.class);

        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any());
        verify(tokenService, never()).revokeAllUserTokens(any(), any());
        assertThat(user.getPasswordHash()).isEqualTo("hashed-password");
    }

    @Test
    void resetPassword_userNotFound_throwsAndNeverConsumesOtp() {
        when(userRepository.findByEmailForUpdate("test@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.resetPassword(resetRequest("123456")))
                .isInstanceOf(OtpNotFoundException.class);

        verify(verificationTokenService, never()).consumeOtp(any(), any(), any(), any());
        verify(userRepository, never()).save(any());
    }

    // ===================== H4: đăng nhập 2 bước =====================

    private void stubPasswordOk() {
        when(userRepository.findByEmailForUpdate("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123", "hashed-password")).thenReturn(true);
    }

    private void stubTokens() {
        when(tokenService.generateAccessToken(user)).thenReturn("access-token");
        when(tokenService.generateRefreshToken(user, IP, USER_AGENT)).thenReturn("refresh-token");
        when(tokenService.getAccessTokenExpiry()).thenReturn(EXPIRES_AT);
    }

    /** Đặt user vào trạng thái "đã qua bước 1, đang chờ mã" */
    private void withPendingChallenge(Instant expiresAt) {
        user.setMfaEnabled(true);
        user.setMfaChallengeHash("stored-hash");
        user.setMfaChallengeExpiresAt(expiresAt);
        when(userRepository.findByMfaChallengeHashForUpdate(anyString())).thenReturn(Optional.of(user));
    }

    @Test
    void login_mfaEnabled_returnsChallenge_withoutIssuingTokens() {
        user.setMfaEnabled(true);
        user.setFailedLoginAttempts(2);
        stubPasswordOk();

        LoginResult result = authService.login(loginRequest("test@example.com", "Password123"), IP, USER_AGENT);

        assertThat(result.refreshToken()).isNull();
        assertThat(result.response().isMfaRequired()).isTrue();
        assertThat(result.response().getMfaToken()).isNotBlank();
        assertThat(result.response().getAccessToken()).isNull();
        // DB chỉ lưu hash, không lưu token gốc
        assertThat(user.getMfaChallengeHash()).isNotNull().isNotEqualTo(result.response().getMfaToken());
        assertThat(user.getMfaChallengeExpiresAt()).isAfter(Instant.now());
        assertThat(user.getFailedLoginAttempts()).isZero();
        // Chưa tính là đăng nhập
        assertThat(user.getLastLoginAt()).isNull();
        verify(tokenService, never()).generateAccessToken(any());
        verify(auditLogService, never()).success(AuditAction.LOGIN, user.getId(), "test@example.com");
    }

    @Test
    void loginWithMfa_correctCode_issuesTokens_andClearsChallenge() {
        withPendingChallenge(Instant.now().plus(5, ChronoUnit.MINUTES));
        when(mfaService.verifyCode(user, "123456")).thenReturn(true);
        stubTokens();

        LoginResult result = authService.loginWithMfa(new MfaLoginRequest("raw-token", "123456"), IP, USER_AGENT);

        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        assertThat(result.response().getAccessToken()).isEqualTo("access-token");
        assertThat(user.getMfaChallengeHash()).isNull();          // dùng 1 lần
        assertThat(user.getMfaChallengeExpiresAt()).isNull();
        assertThat(user.getLastLoginAt()).isNotNull();
        verify(auditLogService).success(AuditAction.LOGIN, user.getId(), "test@example.com");
    }

    @Test
    void loginWithMfa_wrongCode_throwsMfaInvalidCode_andCountsAttempt() {
        withPendingChallenge(Instant.now().plus(5, ChronoUnit.MINUTES));
        when(mfaService.verifyCode(user, "000000")).thenReturn(false);

        assertThatThrownBy(() -> authService.loginWithMfa(new MfaLoginRequest("raw-token", "000000"), IP, USER_AGENT))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.MFA_INVALID_CODE));

        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(user.getMfaChallengeHash()).isNotNull();        // vẫn cho nhập lại
        verify(tokenService, never()).generateAccessToken(any());
    }

    @Test
    void loginWithMfa_fifthWrongCode_locksAccount_andClearsChallenge() {
        withPendingChallenge(Instant.now().plus(5, ChronoUnit.MINUTES));
        user.setFailedLoginAttempts(4);
        when(mfaService.verifyCode(user, "000000")).thenReturn(false);

        assertThatThrownBy(() -> authService.loginWithMfa(new MfaLoginRequest("raw-token", "000000"), IP, USER_AGENT))
                .isInstanceOf(LockedAccountException.class);

        assertThat(user.getLockedUntil()).isAfter(Instant.now());
        assertThat(user.getMfaChallengeHash()).isNull();
    }

    @Test
    void loginWithMfa_expiredChallenge_throwsChallengeExpired_withoutCheckingCode() {
        withPendingChallenge(Instant.now().minus(1, ChronoUnit.MINUTES));

        assertThatThrownBy(() -> authService.loginWithMfa(new MfaLoginRequest("raw-token", "123456"), IP, USER_AGENT))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.MFA_CHALLENGE_EXPIRED));

        assertThat(user.getMfaChallengeHash()).isNull();
        verify(mfaService, never()).verifyCode(any(), anyString());
    }

    @Test
    void loginWithMfa_unknownOrReusedToken_throwsChallengeExpired() {
        when(userRepository.findByMfaChallengeHashForUpdate(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.loginWithMfa(new MfaLoginRequest("used-token", "123456"), IP, USER_AGENT))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.MFA_CHALLENGE_EXPIRED));

        verify(mfaService, never()).verifyCode(any(), anyString());
    }

    // ---------- Đăng nhập bằng Google ----------

    private static final GoogleLoginRequest GOOGLE_REQUEST = new GoogleLoginRequest("google-id-token");
    private static final GoogleTokenVerifier.GoogleProfile GOOGLE_PROFILE =
            new GoogleTokenVerifier.GoogleProfile("google-sub-1", "test@example.com", "Nguyen Van A");

    private void stubGoogleTokens() {
        when(tokenService.generateAccessToken(any(User.class))).thenReturn("access-token");
        when(tokenService.generateRefreshToken(any(User.class), eq(IP), eq(USER_AGENT))).thenReturn("refresh-token");
        when(tokenService.getAccessTokenExpiry()).thenReturn(EXPIRES_AT);
    }

    @Test
    void loginWithGoogle_newEmail_createsActiveAccount_linksInvites_andIssuesTokens() {
        when(googleTokenVerifier.verify("google-id-token")).thenReturn(GOOGLE_PROFILE);
        when(userRepository.findByEmailForUpdate("test@example.com")).thenReturn(Optional.empty());
        when(roleRepository.findByCode("OWNER")).thenReturn(Optional.of(ownerRole));
        when(roleRepository.findByCode("BENEFICIARY")).thenReturn(Optional.of(beneficiaryRole));
        when(passwordEncoder.encode(anyString())).thenReturn("random-hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            if (u.getId() == null) u.setId(UUID.randomUUID());
            return u;
        });
        stubGoogleTokens();

        LoginResult result = authService.loginWithGoogle(GOOGLE_REQUEST, IP, USER_AGENT);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository, atLeastOnce()).save(saved.capture());
        User created = saved.getAllValues().get(0);
        assertThat(created.getEmail()).isEqualTo("test@example.com");
        assertThat(created.getFullName()).isEqualTo("Nguyen Van A");
        assertThat(created.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(created.getRoles()).containsExactlyInAnyOrder(ownerRole, beneficiaryRole);
        verify(verificationTokenService).linkPendingBeneficiaryVaults(created);
        verify(verificationTokenService, never()).issueOtp(any(), any());
        assertThat(result.response().getAccessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
    }

    @Test
    void loginWithGoogle_newEmail_storesRequestedLanguage_andNeverChangesExistingAccount() {
        when(googleTokenVerifier.verify("google-id-token")).thenReturn(GOOGLE_PROFILE);
        when(userRepository.findByEmailForUpdate("test@example.com")).thenReturn(Optional.empty());
        when(roleRepository.findByCode("OWNER")).thenReturn(Optional.of(ownerRole));
        when(roleRepository.findByCode("BENEFICIARY")).thenReturn(Optional.of(beneficiaryRole));
        when(passwordEncoder.encode(anyString())).thenReturn("random-hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            if (u.getId() == null) u.setId(UUID.randomUUID());
            return u;
        });
        stubGoogleTokens();

        authService.loginWithGoogle(new GoogleLoginRequest("google-id-token", "en"), IP, USER_AGENT);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository, atLeastOnce()).save(saved.capture());
        assertThat(saved.getAllValues().get(0).getPreferredLanguage()).isEqualTo(AppLanguage.EN);

        // Tài khoản đã có: ngôn ngữ trong request không ghi đè lựa chọn của người dùng
        user.setStatus(UserStatus.ACTIVE);
        user.setPreferredLanguage(AppLanguage.VI);
        when(userRepository.findByEmailForUpdate("test@example.com")).thenReturn(Optional.of(user));

        authService.loginWithGoogle(new GoogleLoginRequest("google-id-token", "en"), IP, USER_AGENT);

        assertThat(user.getPreferredLanguage()).isEqualTo(AppLanguage.VI);
    }

    @Test
    void loginWithGoogle_pendingAccount_activates_andReplacesPassword() {
        user.setStatus(UserStatus.PENDING);
        when(googleTokenVerifier.verify("google-id-token")).thenReturn(GOOGLE_PROFILE);
        when(userRepository.findByEmailForUpdate("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(anyString())).thenReturn("random-hash");
        stubGoogleTokens();

        authService.loginWithGoogle(GOOGLE_REQUEST, IP, USER_AGENT);

        // Người tạo tài khoản PENDING có thể không phải chủ email -> mật khẩu họ đặt không còn dùng được
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getPasswordHash()).isEqualTo("random-hash");
        verify(verificationTokenService).linkPendingBeneficiaryVaults(user);
        verify(auditLogService).log(AuditAction.EMAIL_VERIFIED, AuditResult.SUCCESS, user.getId(), user.getEmail(),
                null, null, null, "google");
    }

    @Test
    void loginWithGoogle_existingActiveAccount_keepsPassword_andLogsIn() {
        when(googleTokenVerifier.verify("google-id-token")).thenReturn(GOOGLE_PROFILE);
        when(userRepository.findByEmailForUpdate("test@example.com")).thenReturn(Optional.of(user));
        stubGoogleTokens();

        LoginResult result = authService.loginWithGoogle(GOOGLE_REQUEST, IP, USER_AGENT);

        assertThat(user.getPasswordHash()).isEqualTo("hashed-password");
        assertThat(user.getLastLoginAt()).isNotNull();
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        verify(verificationTokenService, never()).linkPendingBeneficiaryVaults(any());
    }

    @Test
    void loginWithGoogle_lockedAccount_isRejected() {
        user.setStatus(UserStatus.LOCKED);
        when(googleTokenVerifier.verify("google-id-token")).thenReturn(GOOGLE_PROFILE);
        when(userRepository.findByEmailForUpdate("test@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.loginWithGoogle(GOOGLE_REQUEST, IP, USER_AGENT))
                .isInstanceOf(LockedAccountException.class);
        verify(tokenService, never()).generateAccessToken(any());
    }

    @Test
    void loginWithGoogle_temporarilyLocked_isRejected() {
        user.setLockedUntil(Instant.now().plus(10, ChronoUnit.MINUTES));
        when(googleTokenVerifier.verify("google-id-token")).thenReturn(GOOGLE_PROFILE);
        when(userRepository.findByEmailForUpdate("test@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.loginWithGoogle(GOOGLE_REQUEST, IP, USER_AGENT))
                .isInstanceOf(LockedAccountException.class);
        verify(tokenService, never()).generateAccessToken(any());
    }

    @Test
    void loginWithGoogle_mfaEnabled_returnsChallenge_withoutTokens() {
        user.setMfaEnabled(true);
        when(googleTokenVerifier.verify("google-id-token")).thenReturn(GOOGLE_PROFILE);
        when(userRepository.findByEmailForUpdate("test@example.com")).thenReturn(Optional.of(user));

        LoginResult result = authService.loginWithGoogle(GOOGLE_REQUEST, IP, USER_AGENT);

        assertThat(result.refreshToken()).isNull();
        assertThat(result.response().isMfaRequired()).isTrue();
        assertThat(result.response().getMfaToken()).isNotBlank();
        assertThat(user.getMfaChallengeHash()).isNotBlank();
        verify(tokenService, never()).generateAccessToken(any());
    }

    @Test
    void loginWithGoogle_invalidToken_propagatesError_andTouchesNoUser() {
        when(googleTokenVerifier.verify("google-id-token"))
                .thenThrow(new BusinessException(ErrorCode.GOOGLE_TOKEN_INVALID, "Invalid Google credential"));

        assertThatThrownBy(() -> authService.loginWithGoogle(GOOGLE_REQUEST, IP, USER_AGENT))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(userRepository);
    }

    @Test
    void googleConfig_hidesClientId_whenDisabled() {
        when(googleTokenVerifier.isEnabled()).thenReturn(false);
        assertThat(authService.googleConfig()).isEqualTo(new GoogleConfigResponse(false, null));
    }
}
