package com.ltld.app.legacyvault.service.tokenservice;

import com.ltld.app.legacyvault.entity.User;

import java.time.Instant;

public interface TokenService {
    String generateAccessToken(User user);
    Instant getAccessTokenExpiry();
    String generateRefreshToken(User user, String ipAddress, String userAgent);
}
