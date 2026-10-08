package com.ltld.app.legacyvault.authtest;

import com.ltld.app.legacyvault.dto.logindto.LoginRequest;
import com.ltld.app.legacyvault.dto.logindto.LoginResponse;
import com.ltld.app.legacyvault.dto.logindto.LoginResult;
import com.ltld.app.legacyvault.entity.Role;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.dto.registerdto.RegisterRequest;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.RevokedReason;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.exception.EmailAlreadyExistsException;
import com.ltld.app.legacyvault.exception.InvalidCredentialException;
import com.ltld.app.legacyvault.exception.LockedAccountException;
import com.ltld.app.legacyvault.exception.NotActiveUserException;
import com.ltld.app.legacyvault.repository.RoleRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.authservice.AuthServiceImpl;
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


    private Role ownerRole;

    private User user;

    @BeforeEach
    void setUp() {
        ownerRole = Role.builder().code("OWNER").build();

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
        assertThat(savedUser.getRoles()).containsExactly(ownerRole);

        verify(verificationTokenService).issueOtp(savedUser);
        verify(auditLogService).success(AuditAction.REGISTER, savedUser.getId(), "test@example.com");
    }

    @Test
    void register_emailWithSpacesAndUpperCase_isNormalizedBeforeSave() {
        RegisterRequest request = validRegisterRequest();
        request.setEmail("  Test@Example.COM ");

        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(roleRepository.findByCode("OWNER")).thenReturn(Optional.of(ownerRole));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("test@example.com");
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

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
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

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123", "hashed-password")).thenReturn(true);
        when(tokenService.generateAccessToken(user)).thenReturn("access-token");
        when(tokenService.generateRefreshToken(user, IP, USER_AGENT)).thenReturn("refresh-token");
        when(tokenService.getAccessTokenExpiry()).thenReturn(EXPIRES_AT);

        authService.login(request, IP, USER_AGENT);

        verify(userRepository).findByEmail("test@example.com");
    }

    @Test
    void login_unknownEmail_throwsInvalidCredential() {
        LoginRequest request = loginRequest("nobody@example.com", "Password123");
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request, IP, USER_AGENT))
                .isInstanceOf(InvalidCredentialException.class);

        verifyNoInteractions(passwordEncoder, tokenService);
        verify(userRepository, never()).save(any());
        verify(auditLogService).failure(AuditAction.LOGIN_FAILED, null,
                "nobody@example.com", "unknown email");
    }

    @Test
    void login_lockedAccount_throwsLockedWithoutCheckingPassword() {
        user.setStatus(UserStatus.LOCKED);
        LoginRequest request = loginRequest("test@example.com", "Password123");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

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
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
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
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
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
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

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

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
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

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
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
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
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


}
