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
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DmsOneClickCheckinServiceImplTest {

    @Mock private DmsConfigRepository dmsConfigRepository;
    @Mock private DmsCheckinRepository dmsCheckinRepository;
    @Mock private ExecutorAssignmentRepository executorAssignmentRepository;
    @Mock private AuditLogService auditLogService;

    private JwtEncoder jwtEncoder;
    private JwtDecoder jwtDecoder;
    private DmsOneClickCheckinServiceImpl oneClickCheckinService;

    private User owner;
    private DmsConfig dmsConfig;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret("0123456789abcdef0123456789abcdef0123456789abcdef");
        JwtConfig jwtConfig = new JwtConfig(props);
        jwtEncoder = jwtConfig.jwtEncoder();
        jwtDecoder = jwtConfig.jwtDecoder();

        oneClickCheckinService = new DmsOneClickCheckinServiceImpl(
                jwtEncoder,
                jwtDecoder,
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
    @DisplayName("F2: Token hợp lệ -> reset chu kỳ DMS, lưu tokenJti và ghi AuditLog DMS_CHECKIN_ONE_CLICK")
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
                eq(AuditAction.DMS_CHECKIN_ONE_CLICK),
                eq(AuditResult.SUCCESS),
                eq(owner.getId()),
                eq("owner@example.com"),
                isNull(),
                eq(DmsActivationProcessor.ENTITY_DMS_CONFIG),
                eq(dmsConfig.getId().toString()),
                contains("jti=")
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
        assertEquals(DmsException.CHECKIN_TOKEN_INVALID, ex.getErrorCode());
        verify(dmsConfigRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("F2: Token hết hạn -> trả 400 CHECKIN_TOKEN_INVALID")
    void checkInWithToken_expiredToken_throwsCheckinTokenInvalid() {
        Instant past = Instant.now().minusSeconds(7200);
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        JwtClaimsSet expiredClaims = JwtClaimsSet.builder()
                .issuer(JwtConfig.ISSUER)
                .audience(List.of(JwtConfig.AUDIENCE))
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
        assertEquals(DmsException.CHECKIN_TOKEN_INVALID, ex.getErrorCode());
    }

    @Test
    @DisplayName("F2: Token không đúng purpose DMS_CHECKIN -> trả 400 CHECKIN_TOKEN_INVALID")
    void checkInWithToken_wrongPurpose_throwsCheckinTokenInvalid() {
        Instant now = Instant.now();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        JwtClaimsSet wrongPurposeClaims = JwtClaimsSet.builder()
                .issuer(JwtConfig.ISSUER)
                .audience(List.of(JwtConfig.AUDIENCE))
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
        assertEquals(DmsException.CHECKIN_TOKEN_INVALID, ex.getErrorCode());
    }
}
