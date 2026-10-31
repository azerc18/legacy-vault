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
import com.ltld.app.legacyvault.repository.DmsCheckinRepository;
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import com.ltld.app.legacyvault.security.JwtConfig;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final DmsConfigRepository dmsConfigRepository;
    private final DmsCheckinRepository dmsCheckinRepository;
    private final ExecutorAssignmentRepository executorAssignmentRepository;
    private final AuditLogService auditLogService;
    private final String backendBaseUrl;
    private final String frontendBaseUrl;
    private final DmsScheduleCalculator scheduleCalculator = new DmsScheduleCalculator();

    public DmsOneClickCheckinServiceImpl(
            JwtEncoder jwtEncoder,
            JwtDecoder jwtDecoder,
            DmsConfigRepository dmsConfigRepository,
            DmsCheckinRepository dmsCheckinRepository,
            ExecutorAssignmentRepository executorAssignmentRepository,
            AuditLogService auditLogService,
            @Value("${app.backend.base-url:${BACKEND_BASE_URL:}}") String backendBaseUrl,
            @Value("${app.frontend.base-url:${FRONTEND_BASE_URL:http://localhost:5173}}") String frontendBaseUrl
    ) {
        this.jwtEncoder = jwtEncoder;
        this.jwtDecoder = jwtDecoder;
        this.dmsConfigRepository = dmsConfigRepository;
        this.dmsCheckinRepository = dmsCheckinRepository;
        this.executorAssignmentRepository = executorAssignmentRepository;
        this.auditLogService = auditLogService;
        this.frontendBaseUrl = normalizeBaseUrl(frontendBaseUrl, "http://localhost:5173");
        this.backendBaseUrl = resolveBackendBaseUrl(backendBaseUrl, this.frontendBaseUrl);
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
                .audience(List.of(JwtConfig.AUDIENCE))
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
            throw DmsException.checkinTokenInvalid("Check-in token is missing.");
        }

        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(rawToken.trim());
        } catch (JwtException | IllegalArgumentException ex) {
            throw DmsException.checkinTokenInvalid("Check-in token is invalid or expired.");
        }

        String purpose = jwt.getClaimAsString(PURPOSE_CLAIM);
        if (!PURPOSE_DMS_CHECKIN.equals(purpose)) {
            throw DmsException.checkinTokenInvalid("Invalid token purpose for DMS check-in.");
        }

        Instant expiresAt = jwt.getExpiresAt();
        if (expiresAt == null || !Instant.now().isBefore(expiresAt)) {
            throw DmsException.checkinTokenInvalid("Check-in token has expired.");
        }

        String jti = jwt.getId();
        if (jti == null || jti.isBlank()) {
            throw DmsException.checkinTokenInvalid("Check-in token is missing jti.");
        }

        UUID ownerId;
        try {
            ownerId = UUID.fromString(jwt.getSubject());
        } catch (RuntimeException ex) {
            throw DmsException.checkinTokenInvalid("Check-in token subject is invalid.");
        }

        if (dmsCheckinRepository.existsByTokenJti(jti)) {
            throw DmsException.checkinTokenInvalid("This check-in link has already been used.");
        }

        DmsConfig config = dmsConfigRepository.findByOwnerIdForUpdate(ownerId)
                .orElseThrow(() -> DmsException.checkinTokenInvalid("Dead Man's Switch is not configured for this account."));

        if (config.getStatus() == DmsStatus.TRIGGERED) {
            throw new DmsException("The Dead Man's Switch has been triggered; check-in is no longer possible.", HttpStatus.CONFLICT);
        }
        if (config.getStatus() == DmsStatus.SUSPENDED) {
            throw new DmsException("The Dead Man's Switch is currently suspended by an administrator.", HttpStatus.CONFLICT);
        }
        if (config.getCheckinIntervalDays() == null || config.getGracePeriodDays() == null || config.getReminderCount() == null) {
            throw DmsException.checkinTokenInvalid("Dead Man's Switch schedule is incomplete.");
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
            throw DmsException.checkinTokenInvalid("This check-in link has already been used.");
        }

        String ownerEmail = config.getOwner() != null ? config.getOwner().getEmail() : null;
        auditLogService.log(
                AuditAction.DMS_CHECKIN_ONE_CLICK,
                AuditResult.SUCCESS,
                ownerId,
                ownerEmail,
                null,
                DmsActivationProcessor.ENTITY_DMS_CONFIG,
                config.getId().toString(),
                DmsActivationProcessor.ownerDetail(ownerId) + ", jti=" + jti
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

    private static String normalizeBaseUrl(String url, String fallback) {
        String raw = (url == null || url.isBlank()) ? fallback : url.trim();
        return raw.endsWith("/") ? raw.substring(0, raw.length() - 1) : raw;
    }
}
