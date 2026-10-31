package com.ltld.app.legacyvault.service.dmsservice;

import com.ltld.app.legacyvault.dto.dmsdto.DmsConfigResponse;
import com.ltld.app.legacyvault.entity.DmsCheckin;
import com.ltld.app.legacyvault.entity.DmsConfig;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class DmsOneClickCheckinServiceImpl implements DmsOneClickCheckinService {

    public static final String PURPOSE_CLAIM = "purpose";
    public static final String PURPOSE_DMS_CHECKIN = "DMS_CHECKIN";
    public static final Duration TOKEN_TTL = Duration.ofDays(7);
    // F2: audience riêng, KHÔNG chứa JwtConfig.AUDIENCE -> token check-in không dùng được như access token.
    public static final String CHECKIN_AUDIENCE = "legacyvault-dms-checkin";
    static final String AUDIT_VIA_ONE_CLICK = "one-click email link";

    private final JwtEncoder jwtEncoder;
    // Decoder riêng (không phải bean): chỉ nhận token có audience CHECKIN_AUDIENCE, nên access token bị từ chối.
    private final JwtDecoder checkinTokenDecoder;
    private final DmsConfigRepository dmsConfigRepository;
    private final DmsCheckinRepository dmsCheckinRepository;
    private final ExecutorAssignmentRepository executorAssignmentRepository;
    private final AuditLogService auditLogService;
    private final String backendBaseUrl;
    private final String frontendBaseUrl;
    private final DmsScheduleCalculator scheduleCalculator = new DmsScheduleCalculator();

    public DmsOneClickCheckinServiceImpl(
            JwtEncoder jwtEncoder,
            JwtProperties jwtProperties,
            DmsConfigRepository dmsConfigRepository,
            DmsCheckinRepository dmsCheckinRepository,
            ExecutorAssignmentRepository executorAssignmentRepository,
            AuditLogService auditLogService,
            @Value("${app.backend.base-url:${BACKEND_BASE_URL:}}") String backendBaseUrl,
            @Value("${app.frontend.base-url:${FRONTEND_BASE_URL:http://localhost:5173}}") String frontendBaseUrl
    ) {
        this.jwtEncoder = jwtEncoder;
        this.checkinTokenDecoder = buildCheckinTokenDecoder(jwtProperties.getSecret());
        this.dmsConfigRepository = dmsConfigRepository;
        this.dmsCheckinRepository = dmsCheckinRepository;
        this.executorAssignmentRepository = executorAssignmentRepository;
        this.auditLogService = auditLogService;
        this.frontendBaseUrl = normalizeBaseUrl(frontendBaseUrl, "http://localhost:5173");
        this.backendBaseUrl = resolveBackendBaseUrl(backendBaseUrl, this.frontendBaseUrl);
    }

    // Cùng khoá HS256 với JwtConfig (security.jwt.secret, UTF-8), nhưng validator đòi issuer JwtConfig.ISSUER
    // và audience CHECKIN_AUDIENCE; token có JwtConfig.AUDIENCE (access token) bị từ chối.
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

    static String resolveBackendBaseUrl(String explicitBackendUrl, String frontendBaseUrl) {
        if (explicitBackendUrl != null && !explicitBackendUrl.isBlank()) {
            return normalizeBaseUrl(explicitBackendUrl, "http://localhost:8084");
        }
        if (frontendBaseUrl != null && !frontendBaseUrl.isBlank()) {
            String trimmed = normalizeBaseUrl(frontendBaseUrl, "http://localhost:5173");
            if (!trimmed.contains("localhost:5173")
                    && !trimmed.contains("127.0.0.1:5173")
                    && !trimmed.contains("localhost:3000")
                    && !trimmed.contains("127.0.0.1:3000")) {
                return trimmed;
            }
        }
        return "http://localhost:8084";
    }

    @Override
    public String generateCheckinToken(UUID ownerId) {
        if (ownerId == null) {
            throw new IllegalArgumentException("ownerId must not be null");
        }
        Instant now = Instant.now();
        String jti = UUID.randomUUID().toString();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtConfig.ISSUER)
                .audience(List.of(CHECKIN_AUDIENCE))
                .subject(ownerId.toString())
                .id(jti)
                .issuedAt(now)
                .expiresAt(now.plus(TOKEN_TTL))
                .claim(PURPOSE_CLAIM, PURPOSE_DMS_CHECKIN)
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    @Override
    public String buildCheckinLink(UUID ownerId) {
        String token = generateCheckinToken(ownerId);
        String base = normalizeBaseUrl(backendBaseUrl, "http://localhost:8084");
        return base + "/api/dms/checkin-link?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    @Override
    @Transactional(readOnly = true)
    public String buildCheckinLinkForConfig(UUID dmsConfigId) {
        if (dmsConfigId == null) {
            return null;
        }
        return dmsConfigRepository.findById(dmsConfigId)
                .map(cfg -> cfg.getOwner() != null ? cfg.getOwner().getId() : null)
                .map(this::buildCheckinLink)
                .orElse(null);
    }

    @Override
    @Transactional
    public DmsConfigResponse checkInWithToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new DmsException(ErrorCode.CHECKIN_TOKEN_INVALID, "Check-in token is missing.");
        }

        Jwt jwt;
        try {
            jwt = checkinTokenDecoder.decode(rawToken.trim());
        } catch (JwtException | IllegalArgumentException ex) {
            throw new DmsException(ErrorCode.CHECKIN_TOKEN_INVALID, "Check-in token is invalid or expired.");
        }

        String purpose = jwt.getClaimAsString(PURPOSE_CLAIM);
        if (!PURPOSE_DMS_CHECKIN.equals(purpose)) {
            throw new DmsException(ErrorCode.CHECKIN_TOKEN_INVALID, "Invalid token purpose for DMS check-in.");
        }

        Instant expiresAt = jwt.getExpiresAt();
        if (expiresAt == null || !Instant.now().isBefore(expiresAt)) {
            throw new DmsException(ErrorCode.CHECKIN_TOKEN_INVALID, "Check-in token has expired.");
        }

        String jti = jwt.getId();
        if (jti == null || jti.isBlank()) {
            throw new DmsException(ErrorCode.CHECKIN_TOKEN_INVALID, "Check-in token is missing jti.");
        }

        UUID ownerId;
        try {
            ownerId = UUID.fromString(jwt.getSubject());
        } catch (RuntimeException ex) {
            throw new DmsException(ErrorCode.CHECKIN_TOKEN_INVALID, "Check-in token subject is invalid.");
        }

        if (dmsCheckinRepository.existsByTokenJti(jti)) {
            throw new DmsException(ErrorCode.CHECKIN_TOKEN_INVALID, "This check-in link has already been used.");
        }

        DmsConfig config = dmsConfigRepository.findByOwnerIdForUpdate(ownerId)
                .orElseThrow(() -> new DmsException(ErrorCode.NOT_FOUND, "Dead Man's Switch is not configured for this account."));

        // Token hợp lệ nhưng trạng thái DMS không cho check-in: cùng mã với DmsConfigServiceImpl.checkIn.
        if (config.getStatus() == DmsStatus.TRIGGERED) {
            throw new DmsException(ErrorCode.CONFLICT, "The Dead Man's Switch has been triggered; check-in is no longer possible.");
        }
        if (config.getStatus() == DmsStatus.SUSPENDED) {
            throw new DmsException(ErrorCode.CONFLICT, "The Dead Man's Switch is currently suspended by an administrator.");
        }
        if (config.getCheckinIntervalDays() == null || config.getGracePeriodDays() == null || config.getReminderCount() == null) {
            throw new DmsException(ErrorCode.CONFLICT, "Dead Man's Switch schedule is incomplete.");
        }

        LocalDateTime now = LocalDateTime.now();
        DmsSchedule schedule = scheduleCalculator.calculate(
                now,
                config.getCheckinIntervalDays(),
                config.getGracePeriodDays(),
                config.getReminderCount()
        );

        config.setStatus(DmsStatus.ACTIVE);
        config.setLastCheckinAt(now);
        config.setRemindersSent(0);
        config.setTriggerDeferrals(0);
        config.setNextCheckinDueAt(schedule.triggerAt());
        config.setNextReminderAt(schedule.nextReminderAt(0).orElse(null));
        dmsConfigRepository.saveAndFlush(config);

        try {
            dmsCheckinRepository.saveAndFlush(DmsCheckin.builder()
                    .dmsConfig(config)
                    .checkinType(DmsCheckinType.OWNER_RESPONDED)
                    .channel(NotificationChannel.EMAIL)
                    .tokenJti(jti)
                    .occurredAt(now)
                    .build());
        } catch (DataIntegrityViolationException ex) {
            throw new DmsException(ErrorCode.CHECKIN_TOKEN_INVALID, "This check-in link has already been used.");
        }

        // Dùng DMS_CHECKIN có sẵn (không thêm giá trị vào cột enum audit_logs.action); detail ghi rõ kênh one-click.
        String ownerEmail = config.getOwner() != null ? config.getOwner().getEmail() : null;
        auditLogService.log(
                AuditAction.DMS_CHECKIN,
                AuditResult.SUCCESS,
                ownerId,
                ownerEmail,
                null,
                DmsActivationProcessor.ENTITY_DMS_CONFIG,
                config.getId().toString(),
                DmsActivationProcessor.ownerDetail(ownerId) + ", via=" + AUDIT_VIA_ONE_CLICK
                        + ", nextCheckinDueAt=" + config.getNextCheckinDueAt() + ", jti=" + jti
        );

        List<ExecutorAssignment> executors = executorAssignmentRepository.findAllByOwnerId(ownerId);
        return DmsConfigResponse.builder()
                .status(config.getStatus())
                .checkinIntervalDays(config.getCheckinIntervalDays())
                .gracePeriodDays(config.getGracePeriodDays())
                .reminderCount(config.getReminderCount())
                .notificationChannel(config.getNotificationChannel())
                .lastCheckinAt(config.getLastCheckinAt())
                .checkinDueAt(schedule.checkinDueAt())
                .triggerAt(config.getNextCheckinDueAt())
                .nextReminderAt(config.getNextReminderAt())
                .remindersSent(config.getRemindersSent())
                .triggeredAt(config.getTriggeredAt())
                .hasUsableExecutor(ExecutorActivationRule.pickActivationTarget(executors).isPresent())
                .build();
    }

    @Override
    @Transactional
    public String checkInAndBuildRedirectUrl(String rawToken) {
        DmsConfigResponse response = checkInWithToken(rawToken);
        String base = normalizeBaseUrl(frontendBaseUrl, "http://localhost:5173");
        String nextDue = response.getCheckinDueAt() != null ? response.getCheckinDueAt().toString() : "";
        return base + "/checkin/done?status=success&nextDue=" + URLEncoder.encode(nextDue, StandardCharsets.UTF_8);
    }

    @Override
    public String buildErrorRedirectUrl(ErrorCode errorCode) {
        ErrorCode code = errorCode != null ? errorCode : ErrorCode.INTERNAL_ERROR;
        String base = normalizeBaseUrl(frontendBaseUrl, "http://localhost:5173");
        return base + "/checkin/done?status=error&errorCode=" + code.name();
    }

    private static String normalizeBaseUrl(String url, String fallback) {
        String raw = (url == null || url.isBlank()) ? fallback : url.trim();
        return raw.endsWith("/") ? raw.substring(0, raw.length() - 1) : raw;
    }
}
