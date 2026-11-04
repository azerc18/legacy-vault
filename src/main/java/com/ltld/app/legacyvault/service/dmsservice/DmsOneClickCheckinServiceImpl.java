package com.ltld.app.legacyvault.service.dmsservice;

import com.ltld.app.legacyvault.dto.dmsdto.DmsConfigResponse;
import com.ltld.app.legacyvault.dto.dmsdto.OneClickCheckinResponse;
import com.ltld.app.legacyvault.entity.DmsConfig;
import com.ltld.app.legacyvault.exception.DmsCheckinLinkException;
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
import com.ltld.app.legacyvault.security.JwtConfig;
import com.ltld.app.legacyvault.security.JwtProperties;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class DmsOneClickCheckinServiceImpl implements DmsOneClickCheckinService {

    public static final String PURPOSE_CLAIM = "purpose";
    public static final String PURPOSE_DMS_CHECKIN = "DMS_CHECKIN";
    // m7: lastCheckinAt (epoch giây UTC) lúc sinh token; không có claim = chưa từng check-in
    public static final String CYCLE_CLAIM = "cyc";
    public static final Duration TOKEN_TTL = Duration.ofDays(7);
    // Audience riêng, KHÔNG chứa JwtConfig.AUDIENCE -> token check-in không dùng được như access token.
    public static final String CHECKIN_AUDIENCE = "legacyvault-dms-checkin";

    private final JwtEncoder jwtEncoder;
    // Decoder riêng (không phải bean): chỉ nhận token có audience CHECKIN_AUDIENCE, nên access token bị từ chối.
    private final JwtDecoder checkinTokenDecoder;
    private final DmsConfigRepository dmsConfigRepository;
    private final DmsConfigService dmsConfigService;

    public DmsOneClickCheckinServiceImpl(JwtEncoder jwtEncoder, JwtProperties jwtProperties,
                                         DmsConfigRepository dmsConfigRepository,
                                         DmsConfigService dmsConfigService) {
        this.jwtEncoder = jwtEncoder;
        this.checkinTokenDecoder = buildCheckinTokenDecoder(jwtProperties.getSecret());
        this.dmsConfigRepository = dmsConfigRepository;
        this.dmsConfigService = dmsConfigService;
    }

    // Cùng khoá HS256 với JwtConfig (security.jwt.secret, UTF-8), nhưng validator đòi issuer JwtConfig.ISSUER
    // và audience CHECKIN_AUDIENCE; token có JwtConfig.AUDIENCE (access token) bị từ chối.
    // createDefaultWithIssuer đã kiểm exp/nbf (lệch 60s), nên không kiểm exp lần nữa.
    private static JwtDecoder buildCheckinTokenDecoder(String secret) {
        SecretKeySpec key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        OAuth2TokenValidator<Jwt> issuer = JwtValidators.createDefaultWithIssuer(JwtConfig.ISSUER);
        OAuth2TokenValidator<Jwt> audience = jwt -> {
            List<String> aud = jwt.getAudience();
            return aud != null && aud.contains(CHECKIN_AUDIENCE) && !aud.contains(JwtConfig.AUDIENCE)
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Invalid audience for DMS check-in token", null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuer, audience));
        return decoder;
    }

    @Override
    @Transactional(readOnly = true)
    public String generateCheckinTokenForConfig(UUID dmsConfigId) {
        return dmsConfigRepository.findById(dmsConfigId)
                .map(this::generateCheckinToken)
                .orElse(null);
    }

    String generateCheckinToken(DmsConfig config) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(JwtConfig.ISSUER)
                .audience(List.of(CHECKIN_AUDIENCE))
                .subject(config.getOwner().getId().toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plus(TOKEN_TTL))
                .claim(PURPOSE_CLAIM, PURPOSE_DMS_CHECKIN);
        Long cycle = DmsConfigServiceImpl.checkinCycleOf(config);
        if (cycle != null) {
            claims.claim(CYCLE_CLAIM, cycle);
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }

    // Không @Transactional: transaction (và khóa DmsConfig) nằm ở DmsConfigService.checkInFromEmailLink.
    @Override
    public OneClickCheckinResponse checkInWithToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw invalid("Check-in token is missing.");
        }
        Jwt jwt;
        try {
            jwt = checkinTokenDecoder.decode(rawToken.trim());
        } catch (JwtException | IllegalArgumentException ex) {
            throw invalid("Check-in token is invalid or expired.");
        }
        if (!PURPOSE_DMS_CHECKIN.equals(jwt.getClaimAsString(PURPOSE_CLAIM))) {
            throw invalid("Invalid token purpose for DMS check-in.");
        }
        String jti = jwt.getId();
        if (jti == null || jti.isBlank()) {
            throw invalid("Check-in token is missing jti.");
        }
        UUID ownerId;
        Long cycle;
        try {
            ownerId = UUID.fromString(jwt.getSubject());
            cycle = jwt.hasClaim(CYCLE_CLAIM) ? ((Number) jwt.getClaim(CYCLE_CLAIM)).longValue() : null;
        } catch (RuntimeException ex) {
            throw invalid("Check-in token claims are invalid.");
        }

        DmsConfigResponse config = dmsConfigService.checkInFromEmailLink(ownerId, jti, cycle);
        return OneClickCheckinResponse.builder()
                .checkinDueAt(config.getCheckinDueAt())
                .triggerAt(config.getTriggerAt())
                .build();
    }

    private static DmsCheckinLinkException invalid(String message) {
        return new DmsCheckinLinkException(DmsCheckinLinkException.Reason.TOKEN_INVALID, message);
    }
}
