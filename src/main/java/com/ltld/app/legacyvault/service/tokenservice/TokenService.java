package com.ltld.app.legacyvault.service.tokenservice;

import com.ltld.app.legacyvault.dto.logindto.LoginResult;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.RevokedReason;

import java.time.Instant;
import java.util.UUID;

public interface TokenService {
    String generateAccessToken(User user);
    Instant getAccessTokenExpiry();
    String generateRefreshToken(User user, String ipAddress, String userAgent);
    void revokeRefreshToken(String rawToken, RevokedReason reason);
    LoginResult refreshAccessToken(String rawToken, String ipAddress, String userAgent);
    int revokeAllUserTokens(UUID userId, RevokedReason reason);
}
