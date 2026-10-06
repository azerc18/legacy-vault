package com.ltld.app.legacyvault.service.tokenservice;

import com.ltld.app.legacyvault.entity.Authority;
import com.ltld.app.legacyvault.entity.RefreshToken;
import com.ltld.app.legacyvault.entity.Role;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.RevokedReason;
import com.ltld.app.legacyvault.repository.RefreshTokenRepository;
import com.ltld.app.legacyvault.security.JwtProperties;
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

@Service
@RequiredArgsConstructor
public class TokenServiceImpl implements TokenService {
    private final RefreshTokenUtil refreshTokenUtil;
    private final JwtEncoder jwtEncoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;

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
                });
    }
}
