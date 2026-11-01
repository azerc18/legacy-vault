package com.ltld.app.legacyvault.service.authservice;

import com.ltld.app.legacyvault.dto.forgotpassworddto.ResetPasswordRequest;
import com.ltld.app.legacyvault.dto.logindto.LoginRequest;
import com.ltld.app.legacyvault.dto.logindto.LoginResponse;
import com.ltld.app.legacyvault.dto.logindto.LoginResult;
import com.ltld.app.legacyvault.dto.logindto.MfaLoginRequest;
import com.ltld.app.legacyvault.dto.registerdto.RegisterRequest;
import com.ltld.app.legacyvault.entity.Role;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.RevokedReason;
import com.ltld.app.legacyvault.enums.TokenType;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.exception.*;
import com.ltld.app.legacyvault.repository.RoleRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.mfaservice.MfaService;
import com.ltld.app.legacyvault.service.tokenservice.TokenService;
import com.ltld.app.legacyvault.service.verificationservice.VerificationTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService{
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RoleRepository roleRepository;
    private final TokenService tokenService;
    private final VerificationTokenService verificationTokenService;
    private final AuditLogService auditLogService;
    private final MfaService mfaService;

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private static final String DUMMY_HASH = new BCryptPasswordEncoder().encode("dummy-code");
    private static final Duration MFA_CHALLENGE_TTL = Duration.ofMinutes(5);
    private static final SecureRandom RANDOM = new SecureRandom();


    @Override
    @Transactional
    public void register(RegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(email)) {
            auditLogService.failure(AuditAction.REGISTER, null, email, "email already exists");
            passwordEncoder.matches(request.getPassword(), DUMMY_HASH);
            throw new EmailAlreadyExistsException("Email already exists.");
        }

        Role defaultRole = roleRepository.findByCode("OWNER")
                .orElseThrow(() -> new RuntimeException("Default role not found in database."));
        Role beneficiaryRole = roleRepository.findByCode("BENEFICIARY")
                .orElseThrow(() -> new RuntimeException("Beneficiary role not found in database."));

        User saved = userRepository.save(User.builder()
                .email(email)
                .fullName(request.getFullName())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .roles(new HashSet<>(Set.of(defaultRole, beneficiaryRole)))
                .build());

        auditLogService.success(AuditAction.REGISTER, saved.getId(), email);
        verificationTokenService.issueOtp(saved, TokenType.EMAIL_VERIFICATION);
    }

    @Override
    @Transactional(noRollbackFor = {
            InvalidCredentialException.class,
            LockedAccountException.class
    })
    public LoginResult login(LoginRequest request, String ipAddress, String userAgent) {
        String email = request.getEmail().trim().toLowerCase();

        User user = userRepository.findByEmailForUpdate(email).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.getPassword(), DUMMY_HASH);
            auditLogService.failure(AuditAction.LOGIN_FAILED, null, email, "unknown email");
            throw new InvalidCredentialException();
        }

        if (user.getStatus() == UserStatus.LOCKED) {
            auditLogService.failure(AuditAction.LOGIN_FAILED, user.getId(), email, "account locked");
            throw new LockedAccountException();
        }

        if (user.getLockedUntil() != null) {
            if (user.getLockedUntil().isAfter(Instant.now())) {
                auditLogService.failure(AuditAction.LOGIN_FAILED, user.getId(), email,
                        "account temporarily locked until " + user.getLockedUntil());
                throw new LockedAccountException();
            }

            user.setLockedUntil(null);
            user.setFailedLoginAttempts(0);
            userRepository.save(user);
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            int attempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(attempts);

            if (attempts >= MAX_FAILED_ATTEMPTS) {
                Instant lockedUntil = Instant.now().plus(LOCK_DURATION);
                user.setLockedUntil(lockedUntil);
                userRepository.save(user);
                auditLogService.failure(AuditAction.ACCOUNT_LOCKED, user.getId(), email,
                        "locked until " + lockedUntil + " after " + attempts + " failed attempts");
                throw new LockedAccountException();
            }
            userRepository.save(user);
            auditLogService.failure(AuditAction.LOGIN_FAILED, user.getId(), email,
                    "wrong password, attempt " + attempts);
            throw new InvalidCredentialException();
        }

        if (user.getStatus() == UserStatus.PENDING) {
            auditLogService.failure(AuditAction.LOGIN_FAILED, user.getId(), email, "email not verified");
            throw new NotActiveUserException();
        }

        user.setFailedLoginAttempts(0);

        // H4: đã bật 2FA -> chưa cấp token, trả phiếu tạm để nhập mã
        if (user.isMfaEnabled()) {
            String rawToken = newChallengeToken();
            user.setMfaChallengeHash(sha256Hex(rawToken));
            user.setMfaChallengeExpiresAt(Instant.now().plus(MFA_CHALLENGE_TTL));
            userRepository.save(user);
            LoginResponse challenge = LoginResponse.builder().mfaRequired(true).mfaToken(rawToken).build();
            return new LoginResult(challenge, null);
        }

        return completeLogin(user, ipAddress, userAgent);
    }

    @Override
    public void logout(String rawRefreshToken) {
        tokenService.revokeRefreshToken(rawRefreshToken, RevokedReason.LOGOUT);
    }

    @Override
    public LoginResult refresh(String rawRefreshToken, String ipAddress, String userAgent) {
        return tokenService.refreshAccessToken(rawRefreshToken, ipAddress, userAgent);
    }

    @Override
    @Transactional(noRollbackFor = {InvalidOtpException.class,
            TooManyAttemptsException.class, ExpiredOtpException.class})
    public void resetPassword(ResetPasswordRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmailForUpdate(email).orElse(null);

        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            auditLogService.failure(AuditAction.PASSWORD_RESET_FAILED,
                    user == null ? null : user.getId(), email, "user not found or not active");
            throw new OtpNotFoundException("OTP not found");
        }

        verificationTokenService.consumeOtp(user, TokenType.PASSWORD_RESET,
                request.getOtp(), AuditAction.PASSWORD_RESET_FAILED);

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        tokenService.revokeAllUserTokens(user.getId(), RevokedReason.PASSWORD_CHANGED);
        auditLogService.success(AuditAction.PASSWORD_RESET_SUCCESS, user.getId(), email);
    }

    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public LoginResult loginWithMfa(MfaLoginRequest request, String ipAddress, String userAgent) {
        User user = userRepository.findByMfaChallengeHashForUpdate(sha256Hex(request.mfaToken()))
                .orElseThrow(() -> new BusinessException(ErrorCode.MFA_CHALLENGE_EXPIRED, "Sign-in session expired"));

        if (user.getMfaChallengeExpiresAt() == null || user.getMfaChallengeExpiresAt().isBefore(Instant.now())) {
            clearChallenge(user);
            throw new BusinessException(ErrorCode.MFA_CHALLENGE_EXPIRED, "Sign-in session expired");
        }
        if (user.getStatus() == UserStatus.LOCKED
                || (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now()))) {
            clearChallenge(user);
            throw new LockedAccountException();
        }

        if (!mfaService.verifyCode(user, request.code())) {
            int attempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(attempts);
            auditLogService.failure(AuditAction.MFA_VERIFY_FAILED, user.getId(), user.getEmail(),
                    "login: wrong code, attempt " + attempts);
            if (attempts >= MAX_FAILED_ATTEMPTS) {
                Instant lockedUntil = Instant.now().plus(LOCK_DURATION);
                user.setLockedUntil(lockedUntil);
                clearChallenge(user);
                auditLogService.failure(AuditAction.ACCOUNT_LOCKED, user.getId(), user.getEmail(),
                        "locked until " + lockedUntil + " after " + attempts + " failed 2FA attempts");
                throw new LockedAccountException();
            }
            userRepository.save(user);
            throw new BusinessException(ErrorCode.MFA_INVALID_CODE, "Invalid verification code");
        }

        user.setMfaChallengeHash(null);       // dùng 1 lần
        user.setMfaChallengeExpiresAt(null);
        user.setFailedLoginAttempts(0);
        return completeLogin(user, ipAddress, userAgent);
    }

    private LoginResult completeLogin(User user, String ipAddress, String userAgent) {
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
        auditLogService.success(AuditAction.LOGIN, user.getId(), user.getEmail());

        String accessToken = tokenService.generateAccessToken(user);
        String refreshToken = tokenService.generateRefreshToken(user, ipAddress, userAgent);
        LoginResponse response = LoginResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresAt(tokenService.getAccessTokenExpiry())
                .build();
        return new LoginResult(response, refreshToken);
    }

    private void clearChallenge(User user) {
        user.setMfaChallengeHash(null);
        user.setMfaChallengeExpiresAt(null);
        userRepository.save(user);
    }

    private static String newChallengeToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256Hex(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
