package com.ltld.app.legacyvault.service.tokenservicetest;

import com.ltld.app.legacyvault.entity.RefreshToken;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.RevokedReason;
import com.ltld.app.legacyvault.repository.RefreshTokenRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.tokenservice.TokenServiceImpl;
import com.ltld.app.legacyvault.utility.RefreshTokenUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TokenServiceRevokeRefreshTokenTest {
    private static final String RAW_TOKEN = "raw-refresh-token";
    private static final String TOKEN_HASH = "hashed-refresh-token";

    @Mock
    private RefreshTokenUtil refreshTokenUtil;

    @Mock
    private JwtEncoder jwtEncoder;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private TokenServiceImpl tokenService;

    private RefreshToken activeToken;

    @BeforeEach
    void setUp() {
        User user = User.builder().id(UUID.randomUUID()).build();
        Instant now = Instant.now();

        activeToken = RefreshToken.builder()
                .user(user)
                .tokenHash(TOKEN_HASH)
                .issuedAt(now)
                .expiresAt(now.plus(7, ChronoUnit.DAYS))
                .build();
    }

    @Test
    void revokeRefreshToken_validToken_revokesWithGivenReasonAndSaves() {
        when(refreshTokenUtil.hashToken(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(refreshTokenRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(activeToken));

        tokenService.revokeRefreshToken(RAW_TOKEN, RevokedReason.LOGOUT);

        assertThat(activeToken.getRevokedAt()).isNotNull();
        assertThat(activeToken.getRevokedReason()).isEqualTo(RevokedReason.LOGOUT);
        verify(refreshTokenRepository, times(1)).save(activeToken);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void revokeRefreshToken_nullOrBlankToken_doesNothing(String rawToken) {
        assertThatCode(() -> tokenService.revokeRefreshToken(rawToken, RevokedReason.LOGOUT))
                .doesNotThrowAnyException();

        verifyNoInteractions(refreshTokenUtil, refreshTokenRepository);
    }

    @Test
    void revokeRefreshToken_unknownToken_doesNotThrowAndDoesNotSave() {
        when(refreshTokenUtil.hashToken(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(refreshTokenRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.empty());

        assertThatCode(() -> tokenService.revokeRefreshToken(RAW_TOKEN, RevokedReason.LOGOUT))
                .doesNotThrowAnyException();

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void revokeRefreshToken_alreadyRevokedToken_keepsOriginalReasonAndDoesNotSave() {
        Instant revokedAt = Instant.now().minus(1, ChronoUnit.HOURS);
        activeToken.setRevokedAt(revokedAt);
        activeToken.setRevokedReason(RevokedReason.ROTATED);

        when(refreshTokenUtil.hashToken(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(refreshTokenRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(activeToken));

        tokenService.revokeRefreshToken(RAW_TOKEN, RevokedReason.LOGOUT);

        assertThat(activeToken.getRevokedReason()).isEqualTo(RevokedReason.ROTATED);
        assertThat(activeToken.getRevokedAt()).isEqualTo(revokedAt);
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void revokeRefreshToken_expiredButNotRevokedToken_stillRevokes() {
        activeToken.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));

        when(refreshTokenUtil.hashToken(RAW_TOKEN)).thenReturn(TOKEN_HASH);
        when(refreshTokenRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(activeToken));

        tokenService.revokeRefreshToken(RAW_TOKEN, RevokedReason.LOGOUT);

        assertThat(activeToken.getRevokedReason()).isEqualTo(RevokedReason.LOGOUT);
        verify(refreshTokenRepository).save(activeToken);
    }
}
