package com.ltld.app.legacyvault.dto.logindto;

public record LoginResult(
        LoginResponse response,
        String refreshToken
) {}
