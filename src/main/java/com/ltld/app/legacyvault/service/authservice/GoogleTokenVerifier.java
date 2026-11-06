package com.ltld.app.legacyvault.service.authservice;

import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Kiểm tra ID token của Google (Sign in with Google): chữ ký RS256 theo khoá công khai của Google,
 * issuer, audience = Client ID của mình, còn hạn, và email đã được Google xác minh.
 * Decoder riêng (không phải bean JwtDecoder chung của resource server).
 */
@Slf4j
@Component
public class GoogleTokenVerifier {
    private static final String JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");

    private final String clientId;
    private volatile JwtDecoder decoder;   // tạo ở lần dùng đầu tiên: không gọi mạng lúc khởi động app

    public GoogleTokenVerifier(@Value("${app.google.client-id:}") String clientId) {
        this.clientId = clientId == null ? "" : clientId.trim();
    }

    public boolean isEnabled() {
        return !clientId.isEmpty();
    }

    public String getClientId() {
        return clientId;
    }

    public GoogleProfile verify(String idToken) {
        if (!isEnabled()) {
            throw new BusinessException(ErrorCode.GOOGLE_LOGIN_DISABLED, "Google sign-in is not configured");
        }
        Jwt jwt;
        try {
            jwt = decoder().decode(idToken);
        } catch (BadJwtException e) {
            throw new BusinessException(ErrorCode.GOOGLE_TOKEN_INVALID, "Invalid Google credential");
        } catch (JwtException e) {
            // Thường là không tải được khoá công khai của Google (mạng)
            log.warn("Could not verify Google ID token: {}", e.getMessage());
            throw new BusinessException(ErrorCode.GOOGLE_TOKEN_INVALID, "Could not verify Google credential");
        }

        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank() || !Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))) {
            throw new BusinessException(ErrorCode.GOOGLE_EMAIL_NOT_VERIFIED, "Google account email is not verified");
        }
        return new GoogleProfile(jwt.getSubject(), email.trim().toLowerCase(Locale.ROOT), jwt.getClaimAsString("name"));
    }

    private JwtDecoder decoder() {
        JwtDecoder d = decoder;
        if (d == null) {
            synchronized (this) {
                d = decoder;
                if (d == null) {
                    NimbusJwtDecoder nimbus = NimbusJwtDecoder.withJwkSetUri(JWKS_URI)
                            .jwsAlgorithm(SignatureAlgorithm.RS256)
                            .build();
                    nimbus.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                            new JwtTimestampValidator(), issuerValidator(), audienceValidator(clientId)));
                    decoder = d = nimbus;
                }
            }
        }
        return d;
    }

    private static OAuth2TokenValidator<Jwt> issuerValidator() {
        return jwt -> {
            String iss = jwt.getClaimAsString("iss");
            return iss != null && ISSUERS.contains(iss)
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Wrong issuer", null));
        };
    }

    // Token phải được cấp cho CHÍNH app này; token của app khác dùng chung tài khoản Google bị từ chối
    private static OAuth2TokenValidator<Jwt> audienceValidator(String clientId) {
        return jwt -> {
            List<String> aud = jwt.getAudience();
            return aud != null && aud.contains(clientId)
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Wrong audience", null));
        };
    }

    public record GoogleProfile(String subject, String email, String name) {}
}
