package com.ltld.app.legacyvault.service.authservice;

import com.ltld.app.legacyvault.dto.logindto.LoginRequest;
import com.ltld.app.legacyvault.dto.logindto.LoginResponse;
import com.ltld.app.legacyvault.dto.logindto.LoginResult;
import com.ltld.app.legacyvault.dto.registerdto.RegisterRequest;
import com.ltld.app.legacyvault.entity.Role;
import com.ltld.app.legacyvault.entity.User;
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
import com.ltld.app.legacyvault.service.tokenservice.TokenService;
import com.ltld.app.legacyvault.service.verificationservice.VerificationTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
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

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);


    @Override
    @Transactional
    public void register(RegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(email)) {
            auditLogService.failure(AuditAction.REGISTER, null, email, "email already exists");
            throw new EmailAlreadyExistsException("Email already exists.");
        }

        Role defaultRole = roleRepository.findByCode("OWNER")
                .orElseThrow(() -> new RuntimeException("Default role not found in database."));

        User saved = userRepository.save(User.builder()
                .email(email)
                .fullName(request.getFullName())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .roles(Set.of(defaultRole))
                .build());

        auditLogService.success(AuditAction.REGISTER, saved.getId(), email);
        verificationTokenService.issueOtp(saved);
    }

    @Override
    @Transactional(noRollbackFor = {
            InvalidCredentialException.class,
            LockedAccountException.class
    })
    public LoginResult login(LoginRequest request, String ipAddress, String userAgent) {
        String email = request.getEmail().trim().toLowerCase();

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
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
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
        auditLogService.success(AuditAction.LOGIN, user.getId(), email);

        String accessToken = tokenService.generateAccessToken(user);
        String refreshToken = tokenService.generateRefreshToken(user, ipAddress, userAgent);

        LoginResponse response = LoginResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresAt(tokenService.getAccessTokenExpiry())
                .build();

        return new LoginResult(response, refreshToken);
    }

    @Override
    public void logout(String rawRefreshToken) {
        tokenService.revokeRefreshToken(rawRefreshToken, RevokedReason.LOGOUT);
    }

    @Override
    public LoginResult refresh(String rawRefreshToken, String ipAddress, String userAgent) {
        return tokenService.refreshAccessToken(rawRefreshToken, ipAddress, userAgent);
    }
}
