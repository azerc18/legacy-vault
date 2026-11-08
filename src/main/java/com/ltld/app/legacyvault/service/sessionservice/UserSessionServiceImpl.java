package com.ltld.app.legacyvault.service.sessionservice;

import com.ltld.app.legacyvault.dto.sessiondto.UserSessionResponse;
import com.ltld.app.legacyvault.entity.RefreshToken;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.RevokedReason;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.exception.UserProfileException;
import com.ltld.app.legacyvault.repository.RefreshTokenRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.utility.RefreshTokenUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserSessionServiceImpl implements UserSessionService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenUtil refreshTokenUtil;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(readOnly = true)
    public List<UserSessionResponse> listActive(UUID userId, String currentRawToken) {
        String currentHash = hashOrNull(currentRawToken);
        return refreshTokenRepository.findActiveByUserId(userId, Instant.now()).stream()
                .map(t -> UserSessionResponse.builder()
                        .id(t.getId())
                        .userAgent(t.getUserAgent())
                        .ipAddress(t.getIpAddress())
                        .lastActiveAt(t.getIssuedAt())
                        .expiresAt(t.getExpiresAt())
                        .current(currentHash != null && currentHash.equals(t.getTokenHash()))
                        .build())
                // thiết bị đang dùng lên đầu, còn lại mới nhất trước
                .sorted(Comparator.comparing(UserSessionResponse::isCurrent).reversed()
                        .thenComparing(UserSessionResponse::getLastActiveAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    @Override
    @Transactional
    public void revoke(UUID userId, UUID sessionId) {
        RefreshToken token = refreshTokenRepository.findById(sessionId)
                .filter(t -> t.getUser().getId().equals(userId))
                .filter(t -> t.getRevokedAt() == null)
                .orElseThrow(() -> new UserProfileException(ErrorCode.NOT_FOUND, "Session not found"));
        token.setRevokedAt(Instant.now());
        token.setRevokedReason(RevokedReason.LOGOUT);
        refreshTokenRepository.save(token);
        auditLogService.log(AuditAction.SESSION_REVOKED, AuditResult.SUCCESS, userId, null,
                "RefreshToken", sessionId.toString(), null);
    }

    @Override
    @Transactional
    public int revokeOthers(UUID userId, String currentRawToken) {
        String currentHash = hashOrNull(currentRawToken);
        int revoked = currentHash == null
                ? refreshTokenRepository.revokeAllActiveByUserId(userId, Instant.now(), RevokedReason.LOGOUT)
                : refreshTokenRepository.revokeAllActiveByUserIdExcept(userId, currentHash, Instant.now(), RevokedReason.LOGOUT);
        if (revoked > 0) {
            auditLogService.log(AuditAction.SESSION_REVOKED, AuditResult.SUCCESS, userId, null,
                    "RefreshToken", null, "others=" + revoked);
        }
        return revoked;
    }

    private String hashOrNull(String raw) {
        return raw == null || raw.isBlank() ? null : refreshTokenUtil.hashToken(raw);
    }
}
