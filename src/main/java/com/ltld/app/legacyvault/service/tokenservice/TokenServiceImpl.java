package com.ltld.app.legacyvault.service.tokenservice;

import com.ltld.app.legacyvault.dto.logindto.LoginResponse;
import com.ltld.app.legacyvault.dto.logindto.LoginResult;
import com.ltld.app.legacyvault.entity.Authority;
import com.ltld.app.legacyvault.entity.RefreshToken;
import com.ltld.app.legacyvault.entity.Role;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.RevokedReason;
import com.ltld.app.legacyvault.enums.UserStatus;
import com.ltld.app.legacyvault.exception.InvalidRefreshTokenException;
import com.ltld.app.legacyvault.exception.LockedAccountException;
import com.ltld.app.legacyvault.repository.RefreshTokenRepository;
import com.ltld.app.legacyvault.security.JwtProperties;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.utility.RefreshTokenUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TokenServiceImpl implements TokenService {
    private final RefreshTokenUtil refreshTokenUtil;
    private final JwtEncoder jwtEncoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;
    private final AuditLogService auditLogService;

    @Override
    public Instant getAccessTokenExpiry() {
        return Instant.now().plusSeconds(jwtProperties.getAccessTokenTtlSeconds());
    }

    @Override
    public String generateRefreshToken(User user, String ipAddress, String userAgent) {
        String rawToken = refreshTokenUtil.generateRawToken();
        Instant now = Instant.now();

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(refreshTokenUtil.hashToken(rawToken))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(jwtProperties.getRefreshTokenTtlSeconds()))
                .ipAddress(ipAddress)
                .userAgent(truncate(userAgent, 255))
                .build();

        refreshTokenRepository.save(refreshToken);

        return rawToken;
    }

    @Override
    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(jwtProperties.getAccessTokenTtlSeconds());

        List<String> roles = user.getRoles().stream()
                .map(Role::getCode)
                .toList();

        List<String> authorities = user.getRoles().stream()
                .flatMap(role -> role.getAuthorities().stream())
                .map(Authority::getCode)
                .distinct()
                .toList();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("legacyvault-auth")
                .audience(List.of("legacyvault-api"))
                .issuedAt(now)
                .expiresAt(expiry)
                .subject(user.getId().toString())
                .claim("roles", roles)
                .claim("authorities", authorities)
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims
        )).getTokenValue();
    }

    private String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    @Override
    @Transactional
    public void revokeRefreshToken(String rawToken, RevokedReason reason) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(refreshTokenUtil.hashToken(rawToken))
                .filter(token -> token.getRevokedAt() == null)
                .ifPresent(token -> {
                    token.setRevokedAt(Instant.now());
                    token.setRevokedReason(reason);
                    refreshTokenRepository.save(token);
                    if (reason == RevokedReason.LOGOUT) {
                        auditLogService.success(AuditAction.LOGOUT, token.getUser().getId(), null);
                    }
                });
    }

    @Override
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public LoginResult refreshAccessToken(String rawToken, String ipAddress, String userAgent) {
        if (rawToken == null || rawToken.isBlank()) {
            auditLogService.failure(AuditAction.TOKEN_REFRESH_FAILED, null, null, "missing token");
            throw new InvalidRefreshTokenException();
        }

        RefreshToken stored = refreshTokenRepository
                .findByTokenHash(refreshTokenUtil.hashToken(rawToken)).orElse(null);
        if (stored == null) {
            auditLogService.failure(AuditAction.TOKEN_REFRESH_FAILED, null, null, "unknown token");
            throw new InvalidRefreshTokenException();
        }

        User user = stored.getUser();

        if(user.getStatus() != UserStatus.ACTIVE ){
            auditLogService.failure(AuditAction.ACCOUNT_LOCKED, null, null, "account locked");
            throw new LockedAccountException();
        }

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
            auditLogService.failure(AuditAction.TOKEN_REFRESH_FAILED, user.getId(), null, "account temporarily locked");
            throw new LockedAccountException();
        }

        if(stored.getRevokedReason() == RevokedReason.ROTATED) {
            int revoked = refreshTokenRepository.revokeAllActiveByUserId(
                    user.getId(), Instant.now(), RevokedReason.REUSE_DETECTED);
            auditLogService.failure(AuditAction.TOKEN_REUSE_DETECTED, user.getId(), null,
                    "rotated token reused, revoked " + revoked + " active tokens, ip=" + ipAddress);
            throw new InvalidRefreshTokenException();
        }

        if (stored.getRevokedAt() != null) {
            auditLogService.failure(AuditAction.TOKEN_REFRESH_FAILED, user.getId(), null,
                    "token already revoked or reused");
            throw new InvalidRefreshTokenException();
        }

        if (stored.getExpiresAt().isBefore(Instant.now())) {
            stored.setRevokedAt(Instant.now());
            stored.setRevokedReason(RevokedReason.EXPIRED);
            refreshTokenRepository.saveAndFlush(stored);
            auditLogService.failure(AuditAction.TOKEN_REFRESH_FAILED, user.getId(), null, "expired");
            throw new InvalidRefreshTokenException("Refresh token has expired. Please login again.");
        }

        stored.setRevokedAt(Instant.now());
        stored.setRevokedReason(RevokedReason.ROTATED);
        refreshTokenRepository.saveAndFlush(stored);

        String newAccessToken  = generateAccessToken(user);
        String newRefreshToken = generateRefreshToken(user, ipAddress, userAgent);

        auditLogService.success(AuditAction.TOKEN_REFRESH, user.getId(), null);

        LoginResponse response = LoginResponse.builder()
                .accessToken(newAccessToken)
                .tokenType("Bearer")
                .expiresAt(getAccessTokenExpiry())
                .build();

        return new LoginResult(response, newRefreshToken);
    }

    @Override
    @Transactional
    public int revokeAllUserTokens(UUID userId, RevokedReason reason) {
        return refreshTokenRepository.revokeAllActiveByUserId(userId, Instant.now(), reason);
    }
}
