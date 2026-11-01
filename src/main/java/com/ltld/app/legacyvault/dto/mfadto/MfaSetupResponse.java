package com.ltld.app.legacyvault.dto.mfadto;

// secret: để nhập tay vào app Authenticator nếu không quét được QR
public record MfaSetupResponse(String secret, String otpauthUri, String qrDataUri) {}