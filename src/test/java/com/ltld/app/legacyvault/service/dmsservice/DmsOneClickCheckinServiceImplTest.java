package com.ltld.app.legacyvault.service.dmsservice;

import com.ltld.app.legacyvault.dto.dmsdto.DmsConfigResponse;
import com.ltld.app.legacyvault.dto.dmsdto.OneClickCheckinResponse;
import com.ltld.app.legacyvault.entity.DmsConfig;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.exception.DmsCheckinLinkException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
import com.ltld.app.legacyvault.security.JwtConfig;
import com.ltld.app.legacyvault.security.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DmsOneClickCheckinServiceImplTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef";

    @Mock private DmsConfigRepository dmsConfigRepository;
    @Mock private DmsConfigService dmsConfigService;

    private JwtEncoder jwtEncoder;
    // Decoder dùng chung cho access token (bean JwtDecoder thật)
    private JwtDecoder apiJwtDecoder;
    private DmsOneClickCheckinServiceImpl service;

    private User owner;
    private DmsConfig dmsConfig;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret(SECRET);
        JwtConfig jwtConfig = new JwtConfig(props);
        jwtEncoder = jwtConfig.jwtEncoder();
        apiJwtDecoder = jwtConfig.jwtDecoder();
        service = new DmsOneClickCheckinServiceImpl(jwtEncoder, props, dmsConfigRepository, dmsConfigService);

        owner = User.builder().id(UUID.randomUUID()).email("owner@example.com").build();
        dmsConfig = DmsConfig.builder()
                .id(UUID.randomUUID())
                .owner(owner)
                .lastCheckinAt(LocalDateTime.of(2026, 10, 5, 9, 30, 15))
                .build();
    }

    @Test
    @DisplayName("F2: token hợp lệ -> giao cho DmsConfigService.checkInFromEmailLink với ownerId, jti và cyc của token")
    void checkInWithToken_validToken_delegatesWithJtiAndCycle() {
        String token = service.generateCheckinToken(dmsConfig);
        Jwt parsed = decodeWithoutValidation(token);
        long cycle = dmsConfig.getLastCheckinAt().toEpochSecond(ZoneOffset.UTC);
        LocalDateTime due = LocalDateTime.of(2026, 12, 1, 9, 0);
        when(dmsConfigService.checkInFromEmailLink(owner.getId(), parsed.getId(), cycle))
                .thenReturn(DmsConfigResponse.builder().checkinDueAt(due).triggerAt(due.plusDays(7)).build());

        OneClickCheckinResponse res = service.checkInWithToken(token);

        assertEquals(due, res.getCheckinDueAt());
        assertEquals(due.plusDays(7), res.getTriggerAt());
    }

    @Test
    @DisplayName("F2 (m7): Owner chưa từng check-in -> token không có cyc, gửi cycle null")
    void checkInWithToken_noLastCheckin_passesNullCycle() {
        dmsConfig.setLastCheckinAt(null);
        String token = service.generateCheckinToken(dmsConfig);
        Jwt parsed = decodeWithoutValidation(token);
        assertFalse(parsed.hasClaim(DmsOneClickCheckinServiceImpl.CYCLE_CLAIM));
        when(dmsConfigService.checkInFromEmailLink(owner.getId(), parsed.getId(), null))
                .thenReturn(DmsConfigResponse.builder().build());

        service.checkInWithToken(token);

        verify(dmsConfigService).checkInFromEmailLink(owner.getId(), parsed.getId(), null);
    }

    @Test
    @DisplayName("F2: token mang audience riêng, purpose, jti, cyc và TTL 7 ngày; JwtDecoder chung (access token) từ chối")
    void generateCheckinToken_claims_andRejectedByApiDecoder() {
        String token = service.generateCheckinToken(dmsConfig);

        Jwt parsed = decodeWithoutValidation(token);
        assertEquals(List.of(DmsOneClickCheckinServiceImpl.CHECKIN_AUDIENCE), parsed.getAudience());
        assertFalse(parsed.getAudience().contains(JwtConfig.AUDIENCE));
        assertEquals(JwtConfig.ISSUER, parsed.getClaimAsString("iss"));
        assertEquals(owner.getId().toString(), parsed.getSubject());
        assertEquals(DmsOneClickCheckinServiceImpl.PURPOSE_DMS_CHECKIN,
                parsed.getClaimAsString(DmsOneClickCheckinServiceImpl.PURPOSE_CLAIM));
        assertEquals(dmsConfig.getLastCheckinAt().toEpochSecond(ZoneOffset.UTC),
                ((Number) parsed.getClaim(DmsOneClickCheckinServiceImpl.CYCLE_CLAIM)).longValue());
        assertNotNull(parsed.getId());
        assertEquals(DmsOneClickCheckinServiceImpl.TOKEN_TTL,
                Duration.between(parsed.getIssuedAt(), parsed.getExpiresAt()));

        assertThrows(JwtException.class, () -> apiJwtDecoder.decode(token));
    }

    @Test
    @DisplayName("F2: mỗi lần sinh token có jti khác nhau")
    void generateCheckinToken_uniqueJti() {
        String a = decodeWithoutValidation(service.generateCheckinToken(dmsConfig)).getId();
        String b = decodeWithoutValidation(service.generateCheckinToken(dmsConfig)).getId();
        assertFalse(a.equals(b));
    }

    @Test
    @DisplayName("F2: sinh token theo dmsConfigId; không có cấu hình -> null (mail gửi không kèm link)")
    void generateCheckinTokenForConfig_unknownConfig_returnsNull() {
        UUID missing = UUID.randomUUID();
        when(dmsConfigRepository.findById(missing)).thenReturn(Optional.empty());
        when(dmsConfigRepository.findById(dmsConfig.getId())).thenReturn(Optional.of(dmsConfig));

        assertNull(service.generateCheckinTokenForConfig(missing));
        assertNotNull(service.generateCheckinTokenForConfig(dmsConfig.getId()));
    }

    @Test
    @DisplayName("F2: token hết hạn -> 400 CHECKIN_TOKEN_INVALID, reason TOKEN_INVALID")
    void checkInWithToken_expiredToken_rejected() {
        Instant past = Instant.now().minusSeconds(7200);
        assertTokenInvalid(sign(c -> c.issuedAt(past.minusSeconds(3600)).expiresAt(past)));
    }

    @Test
    @DisplayName("F2: purpose khác DMS_CHECKIN -> 400 CHECKIN_TOKEN_INVALID")
    void checkInWithToken_wrongPurpose_rejected() {
        assertTokenInvalid(sign(c -> c.claim(DmsOneClickCheckinServiceImpl.PURPOSE_CLAIM, "ACCESS_TOKEN")));
    }

    @Test
    @DisplayName("F2: thiếu jti -> 400 CHECKIN_TOKEN_INVALID")
    void checkInWithToken_missingJti_rejected() {
        assertTokenInvalid(sign(c -> c.claims(m -> m.remove("jti"))));
    }

    @Test
    @DisplayName("F2: subject không phải UUID -> 400 CHECKIN_TOKEN_INVALID")
    void checkInWithToken_badSubject_rejected() {
        assertTokenInvalid(sign(c -> c.subject("not-a-uuid")));
    }

    @Test
    @DisplayName("F2: cyc không phải số -> 400 CHECKIN_TOKEN_INVALID")
    void checkInWithToken_badCycle_rejected() {
        assertTokenInvalid(sign(c -> c.claim(DmsOneClickCheckinServiceImpl.CYCLE_CLAIM, "yesterday")));
    }

    @Test
    @DisplayName("F2: access token thường (audience legacyvault-api) -> 400 CHECKIN_TOKEN_INVALID, không đụng DMS")
    void checkInWithToken_accessToken_rejected() {
        String accessToken = accessToken(false);
        assertDoesNotThrow(() -> apiJwtDecoder.decode(accessToken)); // đúng là access token hợp lệ
        assertTokenInvalid(accessToken);
    }

    @Test
    @DisplayName("F2: có purpose DMS_CHECKIN nhưng audience legacyvault-api -> vẫn bị từ chối (audience quyết định)")
    void checkInWithToken_checkinPurposeButApiAudience_rejected() {
        assertTokenInvalid(accessToken(true));
    }

    @Test
    @DisplayName("F2: token rác / thiếu -> 400 CHECKIN_TOKEN_INVALID")
    void checkInWithToken_malformedOrBlank_rejected() {
        assertTokenInvalid("not-a-jwt");
        assertTokenInvalid("  ");
        assertTokenInvalid(null);
    }

    @Test
    @DisplayName("F2: lỗi trạng thái DMS từ DmsConfigService được giữ nguyên (reason TRIGGERED...)")
    void checkInWithToken_serviceError_propagates() {
        String token = service.generateCheckinToken(dmsConfig);
        DmsCheckinLinkException triggered =
                new DmsCheckinLinkException(DmsCheckinLinkException.Reason.TRIGGERED, "triggered");
        when(dmsConfigService.checkInFromEmailLink(org.mockito.ArgumentMatchers.eq(owner.getId()),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong()))
                .thenThrow(triggered);

        DmsCheckinLinkException ex = assertThrows(DmsCheckinLinkException.class, () -> service.checkInWithToken(token));
        assertEquals(DmsCheckinLinkException.Reason.TRIGGERED, ex.getReason());
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    private void assertTokenInvalid(String token) {
        DmsCheckinLinkException ex = assertThrows(DmsCheckinLinkException.class, () -> service.checkInWithToken(token));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals(ErrorCode.CHECKIN_TOKEN_INVALID, ex.getErrorCode());
        assertEquals(DmsCheckinLinkException.Reason.TOKEN_INVALID, ex.getReason());
        verifyNoInteractions(dmsConfigService);
    }

    // Token check-in hợp lệ rồi sửa theo từng case.
    private String sign(Consumer<JwtClaimsSet.Builder> tweak) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(JwtConfig.ISSUER)
                .audience(List.of(DmsOneClickCheckinServiceImpl.CHECKIN_AUDIENCE))
                .subject(owner.getId().toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim(DmsOneClickCheckinServiceImpl.PURPOSE_CLAIM, DmsOneClickCheckinServiceImpl.PURPOSE_DMS_CHECKIN);
        tweak.accept(claims);
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims.build())).getTokenValue();
    }

    // Access token giống TokenServiceImpl.generateAccessToken; withCheckinPurpose = thêm purpose DMS_CHECKIN để
    // chứng minh chính audience (không phải purpose) làm token bị từ chối.
    private String accessToken(boolean withCheckinPurpose) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(JwtConfig.ISSUER)
                .audience(List.of(JwtConfig.AUDIENCE))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(900))
                .subject(owner.getId().toString())
                .id(UUID.randomUUID().toString())
                .claim("roles", List.of("OWNER"))
                .claim("authorities", List.of("VAULT_READ"));
        if (withCheckinPurpose) {
            claims.claim(DmsOneClickCheckinServiceImpl.PURPOSE_CLAIM, DmsOneClickCheckinServiceImpl.PURPOSE_DMS_CHECKIN);
        }
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
    }

    // Chỉ kiểm chữ ký, bỏ validator: để đọc claim của token check-in trong test.
    private static Jwt decodeWithoutValidation(String token) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(jwt -> OAuth2TokenValidatorResult.success());
        return decoder.decode(token);
    }
}
