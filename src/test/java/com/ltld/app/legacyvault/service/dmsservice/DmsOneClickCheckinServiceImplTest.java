package com.ltld.app.legacyvault.service.dmsservice;

import com.ltld.app.legacyvault.dto.dmsdto.DmsConfigResponse;
import com.ltld.app.legacyvault.entity.DmsCheckin;
import com.ltld.app.legacyvault.entity.DmsConfig;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.DmsCheckinType;
import com.ltld.app.legacyvault.enums.DmsStatus;
import com.ltld.app.legacyvault.enums.NotificationChannel;
import com.ltld.app.legacyvault.exception.DmsException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.repository.DmsCheckinRepository;
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import com.ltld.app.legacyvault.security.JwtConfig;
import com.ltld.app.legacyvault.security.JwtProperties;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalMatchers.and;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DmsOneClickCheckinServiceImplTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef";

    @Mock private DmsConfigRepository dmsConfigRepository;
    @Mock private DmsCheckinRepository dmsCheckinRepository;
    @Mock private ExecutorAssignmentRepository executorAssignmentRepository;
    @Mock private AuditLogService auditLogService;

    private JwtEncoder jwtEncoder;
    // Decoder dùng chung cho access token (bean JwtDecoder thật)
    private JwtDecoder apiJwtDecoder;
    private DmsOneClickCheckinServiceImpl oneClickCheckinService;

    private User owner;
    private DmsConfig dmsConfig;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret(SECRET);
        JwtConfig jwtConfig = new JwtConfig(props);
        jwtEncoder = jwtConfig.jwtEncoder();
        apiJwtDecoder = jwtConfig.jwtDecoder();

        oneClickCheckinService = new DmsOneClickCheckinServiceImpl(
                jwtEncoder,
                props,
                dmsConfigRepository,
                dmsCheckinRepository,
                executorAssignmentRepository,
                auditLogService,
                "http://localhost:8084",
                "http://localhost:5173"
        );

        owner = User.builder()
                .id(UUID.randomUUID())
                .email("owner@example.com")
                .fullName("Owner User")
                .build();

        dmsConfig = DmsConfig.builder()
                .id(UUID.randomUUID())
                .owner(owner)
                .status(DmsStatus.ACTIVE)
                .checkinIntervalDays(30)
                .gracePeriodDays(7)
                .reminderCount(3)
                .notificationChannel(NotificationChannel.EMAIL)
                .lastCheckinAt(LocalDateTime.now().minusDays(28))
                .nextCheckinDueAt(LocalDateTime.now().plusDays(9))
                .remindersSent(2)
                .build();
    }

    @Test
    @DisplayName("F2: Token hợp lệ -> reset chu kỳ DMS, lưu tokenJti và ghi AuditLog DMS_CHECKIN (one-click email link)")
    void checkInWithToken_validToken_resetsCycleAndLogsAudit() {
        String token = oneClickCheckinService.generateCheckinToken(owner.getId());

        when(dmsCheckinRepository.existsByTokenJti(any())).thenReturn(false);
        when(dmsConfigRepository.findByOwnerIdForUpdate(owner.getId())).thenReturn(Optional.of(dmsConfig));
        when(executorAssignmentRepository.findAllByOwnerId(owner.getId())).thenReturn(List.of());

        DmsConfigResponse res = oneClickCheckinService.checkInWithToken(token);

        assertEquals(DmsStatus.ACTIVE, res.getStatus());
        assertEquals(0, res.getRemindersSent());
        assertNotNull(res.getLastCheckinAt());

        ArgumentCaptor<DmsCheckin> checkinCaptor = ArgumentCaptor.forClass(DmsCheckin.class);
        verify(dmsCheckinRepository).saveAndFlush(checkinCaptor.capture());
        DmsCheckin saved = checkinCaptor.getValue();
        assertEquals(DmsCheckinType.OWNER_RESPONDED, saved.getCheckinType());
        assertNotNull(saved.getTokenJti());

        verify(auditLogService).log(
                eq(AuditAction.DMS_CHECKIN),
                eq(AuditResult.SUCCESS),
                eq(owner.getId()),
                eq("owner@example.com"),
                isNull(),
                eq(DmsActivationProcessor.ENTITY_DMS_CONFIG),
                eq(dmsConfig.getId().toString()),
                and(contains("via=one-click email link"), contains("jti=" + saved.getTokenJti()))
        );
    }

    @Test
    @DisplayName("F2: Dùng lại token lần 2 -> trả 400 CHECKIN_TOKEN_INVALID")
    void checkInWithToken_reusedToken_throwsCheckinTokenInvalid() {
        String token = oneClickCheckinService.generateCheckinToken(owner.getId());

        when(dmsCheckinRepository.existsByTokenJti(any())).thenReturn(true);

        DmsException ex = assertThrows(DmsException.class,
                () -> oneClickCheckinService.checkInWithToken(token));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals(ErrorCode.CHECKIN_TOKEN_INVALID, ex.getErrorCode());
        verify(dmsConfigRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("F2: Token hết hạn -> trả 400 CHECKIN_TOKEN_INVALID")
    void checkInWithToken_expiredToken_throwsCheckinTokenInvalid() {
        Instant past = Instant.now().minusSeconds(7200);
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        JwtClaimsSet expiredClaims = JwtClaimsSet.builder()
                .issuer(JwtConfig.ISSUER)
                .audience(List.of(DmsOneClickCheckinServiceImpl.CHECKIN_AUDIENCE))
                .subject(owner.getId().toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(past.minusSeconds(3600))
                .expiresAt(past)
                .claim(DmsOneClickCheckinServiceImpl.PURPOSE_CLAIM, DmsOneClickCheckinServiceImpl.PURPOSE_DMS_CHECKIN)
                .build();
        String expiredToken = jwtEncoder.encode(JwtEncoderParameters.from(header, expiredClaims)).getTokenValue();

        DmsException ex = assertThrows(DmsException.class,
                () -> oneClickCheckinService.checkInWithToken(expiredToken));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals(ErrorCode.CHECKIN_TOKEN_INVALID, ex.getErrorCode());
    }

    @Test
    @DisplayName("F2: Token không đúng purpose DMS_CHECKIN -> trả 400 CHECKIN_TOKEN_INVALID")
    void checkInWithToken_wrongPurpose_throwsCheckinTokenInvalid() {
        Instant now = Instant.now();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        JwtClaimsSet wrongPurposeClaims = JwtClaimsSet.builder()
                .issuer(JwtConfig.ISSUER)
                .audience(List.of(DmsOneClickCheckinServiceImpl.CHECKIN_AUDIENCE))
                .subject(owner.getId().toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("purpose", "ACCESS_TOKEN")
                .build();
        String wrongToken = jwtEncoder.encode(JwtEncoderParameters.from(header, wrongPurposeClaims)).getTokenValue();

        DmsException ex = assertThrows(DmsException.class,
                () -> oneClickCheckinService.checkInWithToken(wrongToken));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals(ErrorCode.CHECKIN_TOKEN_INVALID, ex.getErrorCode());
    }

    @Test
    @DisplayName("F2: Token check-in có audience riêng, không chứa legacyvault-api -> JwtDecoder chung (access token) từ chối")
    void generateCheckinToken_usesDedicatedAudience_rejectedByApiDecoder() {
        String token = oneClickCheckinService.generateCheckinToken(owner.getId());

        Jwt parsed = jwtDecoderWithoutValidation().decode(token);
        assertEquals(List.of(DmsOneClickCheckinServiceImpl.CHECKIN_AUDIENCE), parsed.getAudience());
        assertFalse(parsed.getAudience().contains(JwtConfig.AUDIENCE));
        assertEquals(JwtConfig.ISSUER, parsed.getClaimAsString("iss"));
        assertEquals(DmsOneClickCheckinServiceImpl.PURPOSE_DMS_CHECKIN,
                parsed.getClaimAsString(DmsOneClickCheckinServiceImpl.PURPOSE_CLAIM));
        assertNotNull(parsed.getId());
        assertEquals(DmsOneClickCheckinServiceImpl.TOKEN_TTL,
                Duration.between(parsed.getIssuedAt(), parsed.getExpiresAt()));

        assertThrows(JwtException.class, () -> apiJwtDecoder.decode(token));
    }

    @Test
    @DisplayName("F2: Access token thường (audience legacyvault-api) -> 400 CHECKIN_TOKEN_INVALID, không đụng DB")
    void checkInWithToken_accessToken_throwsCheckinTokenInvalid() {
        String accessToken = accessTokenFor(owner.getId(), false);
        assertDoesNotThrow(() -> apiJwtDecoder.decode(accessToken)); // đúng là access token hợp lệ

        DmsException ex = assertThrows(DmsException.class,
                () -> oneClickCheckinService.checkInWithToken(accessToken));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals(ErrorCode.CHECKIN_TOKEN_INVALID, ex.getErrorCode());
        verifyNoInteractions(dmsCheckinRepository, dmsConfigRepository, auditLogService);
    }

    @Test
    @DisplayName("F2: Có purpose DMS_CHECKIN nhưng audience legacyvault-api -> vẫn bị từ chối (audience quyết định)")
    void checkInWithToken_checkinPurposeButApiAudience_throwsCheckinTokenInvalid() {
        String token = accessTokenFor(owner.getId(), true);

        DmsException ex = assertThrows(DmsException.class,
                () -> oneClickCheckinService.checkInWithToken(token));

        assertEquals(ErrorCode.CHECKIN_TOKEN_INVALID, ex.getErrorCode());
        verifyNoInteractions(dmsCheckinRepository, dmsConfigRepository, auditLogService);
    }

    @Test
    @DisplayName("F2: URL lỗi trỏ về FE /checkin/done với status=error và tên ErrorCode")
    void buildErrorRedirectUrl_usesFrontendBaseAndErrorCodeName() {
        assertEquals("http://localhost:5173/checkin/done?status=error&errorCode=CHECKIN_TOKEN_INVALID",
                oneClickCheckinService.buildErrorRedirectUrl(ErrorCode.CHECKIN_TOKEN_INVALID));
    }

    @Test
    @DisplayName("F2: Token không giải mã được (sai chữ ký / rác) -> trả 400 CHECKIN_TOKEN_INVALID")
    void checkInWithToken_malformedToken_throwsCheckinTokenInvalid() {
        DmsException ex = assertThrows(DmsException.class,
                () -> oneClickCheckinService.checkInWithToken("not-a-jwt"));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals(ErrorCode.CHECKIN_TOKEN_INVALID, ex.getErrorCode());
        verifyNoInteractions(dmsCheckinRepository, dmsConfigRepository);
    }

    @Test
    @DisplayName("F2: Token hợp lệ nhưng DMS đã TRIGGERED -> trả 409 CONFLICT, không ghi check-in")
    void checkInWithToken_triggeredDms_throwsConflict() {
        String token = oneClickCheckinService.generateCheckinToken(owner.getId());
        dmsConfig.setStatus(DmsStatus.TRIGGERED);
        when(dmsCheckinRepository.existsByTokenJti(any())).thenReturn(false);
        when(dmsConfigRepository.findByOwnerIdForUpdate(owner.getId())).thenReturn(Optional.of(dmsConfig));

        DmsException ex = assertThrows(DmsException.class,
                () -> oneClickCheckinService.checkInWithToken(token));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
        verify(dmsCheckinRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("F2: Token hợp lệ nhưng Owner chưa cấu hình DMS -> trả 404 NOT_FOUND")
    void checkInWithToken_notConfigured_throwsNotFound() {
        String token = oneClickCheckinService.generateCheckinToken(owner.getId());
        when(dmsCheckinRepository.existsByTokenJti(any())).thenReturn(false);
        when(dmsConfigRepository.findByOwnerIdForUpdate(owner.getId())).thenReturn(Optional.empty());

        DmsException ex = assertThrows(DmsException.class,
                () -> oneClickCheckinService.checkInWithToken(token));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    // Access token giống TokenServiceImpl.generateAccessToken; withCheckinPurpose = thêm purpose DMS_CHECKIN để
    // chứng minh chính audience (không phải purpose) làm token bị từ chối.
    private String accessTokenFor(UUID userId, boolean withCheckinPurpose) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(JwtConfig.ISSUER)
                .audience(List.of(JwtConfig.AUDIENCE))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(900))
                .subject(userId.toString())
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
    private static JwtDecoder jwtDecoderWithoutValidation() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(jwt -> OAuth2TokenValidatorResult.success());
        return decoder;
    }
}
