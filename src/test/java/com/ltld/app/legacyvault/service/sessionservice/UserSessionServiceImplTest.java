package com.ltld.app.legacyvault.service.sessionservice;

import com.ltld.app.legacyvault.dto.sessiondto.UserSessionResponse;
import com.ltld.app.legacyvault.entity.RefreshToken;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.RevokedReason;
import com.ltld.app.legacyvault.exception.UserProfileException;
import com.ltld.app.legacyvault.repository.RefreshTokenRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.utility.RefreshTokenUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSessionServiceImplTest {

    @Mock private RefreshTokenRepository repository;
    @Mock private AuditLogService auditLogService;
    private final RefreshTokenUtil util = new RefreshTokenUtil();
    private UserSessionServiceImpl service;

    private final UUID userId = UUID.randomUUID();
    private User user;

    @BeforeEach
    void setUp() {
        service = new UserSessionServiceImpl(repository, util, auditLogService);
        user = new User();
        user.setId(userId);
    }

    private RefreshToken token(String raw, Instant issuedAt) {
        return RefreshToken.builder().id(UUID.randomUUID()).user(user)
                .tokenHash(util.hashToken(raw)).issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(3600)).userAgent("UA-" + raw).ipAddress("10.0.0.1").build();
    }

    @Test
    void listActive_marksCurrentAndPutsItFirst() {
        Instant now = Instant.now();
        RefreshToken older = token("phone", now.minusSeconds(600));
        RefreshToken mine = token("laptop", now.minusSeconds(3000));
        RefreshToken newest = token("tablet", now.minusSeconds(60));
        when(repository.findActiveByUserId(eq(userId), any())).thenReturn(List.of(older, mine, newest));

        List<UserSessionResponse> list = service.listActive(userId, "laptop");

        assertThat(list).extracting(UserSessionResponse::getId)
                .containsExactly(mine.getId(), newest.getId(), older.getId());
        assertThat(list.get(0).isCurrent()).isTrue();
        assertThat(list).filteredOn(UserSessionResponse::isCurrent).hasSize(1);
        assertThat(list.get(0).getUserAgent()).isEqualTo("UA-laptop");
    }

    @Test
    void listActive_withoutCookie_hasNoCurrent() {
        when(repository.findActiveByUserId(eq(userId), any())).thenReturn(List.of(token("a", Instant.now())));

        assertThat(service.listActive(userId, null)).noneMatch(UserSessionResponse::isCurrent);
    }

    @Test
    void revoke_ownActiveSession_setsLogoutAndAudits() {
        RefreshToken t = token("phone", Instant.now());
        when(repository.findById(t.getId())).thenReturn(Optional.of(t));

        service.revoke(userId, t.getId());

        assertThat(t.getRevokedAt()).isNotNull();
        assertThat(t.getRevokedReason()).isEqualTo(RevokedReason.LOGOUT);
        verify(repository).save(t);
        verify(auditLogService).log(eq(AuditAction.SESSION_REVOKED), eq(AuditResult.SUCCESS), eq(userId), isNull(),
                eq("RefreshToken"), eq(t.getId().toString()), isNull());
    }

    @Test
    void revoke_sessionOfAnotherUser_returns404AndChangesNothing() {
        User other = new User();
        other.setId(UUID.randomUUID());
        RefreshToken t = token("x", Instant.now());
        t.setUser(other);
        when(repository.findById(t.getId())).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> service.revoke(userId, t.getId()))
                .isInstanceOf(UserProfileException.class)
                .extracting(e -> ((UserProfileException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(t.getRevokedAt()).isNull();
        verify(repository, never()).save(any());
    }

    @Test
    void revoke_alreadyRevoked_returns404() {
        RefreshToken t = token("x", Instant.now());
        t.setRevokedAt(Instant.now());
        when(repository.findById(t.getId())).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> service.revoke(userId, t.getId())).isInstanceOf(UserProfileException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void revokeOthers_keepsCurrentToken() {
        when(repository.revokeAllActiveByUserIdExcept(eq(userId), eq(util.hashToken("laptop")), any(), eq(RevokedReason.LOGOUT)))
                .thenReturn(2);

        assertThat(service.revokeOthers(userId, "laptop")).isEqualTo(2);
        verify(repository, never()).revokeAllActiveByUserId(any(), any(), any());
        verify(auditLogService).log(eq(AuditAction.SESSION_REVOKED), eq(AuditResult.SUCCESS), eq(userId), isNull(),
                eq("RefreshToken"), isNull(), eq("others=2"));
    }

    @Test
    void revokeOthers_withoutCookie_revokesAll() {
        when(repository.revokeAllActiveByUserId(eq(userId), any(), eq(RevokedReason.LOGOUT))).thenReturn(3);

        assertThat(service.revokeOthers(userId, " ")).isEqualTo(3);
        verify(repository, never()).revokeAllActiveByUserIdExcept(any(), anyString(), any(), any());
    }

    @Test
    void revokeOthers_nothingToRevoke_doesNotAudit() {
        when(repository.revokeAllActiveByUserIdExcept(eq(userId), anyString(), any(), any())).thenReturn(0);

        assertThat(service.revokeOthers(userId, "laptop")).isZero();
        verify(auditLogService, never()).log(any(), any(), any(), any(), any(), any(), any());
    }
}
