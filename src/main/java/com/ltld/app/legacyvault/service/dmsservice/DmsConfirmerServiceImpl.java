package com.ltld.app.legacyvault.service.dmsservice;

import com.ltld.app.legacyvault.dto.dmsdto.DmsConfirmationDecisionResponse;
import com.ltld.app.legacyvault.dto.dmsdto.DmsConfirmerRequest;
import com.ltld.app.legacyvault.dto.dmsdto.DmsConfirmerResponse;
import com.ltld.app.legacyvault.entity.DmsConfig;
import com.ltld.app.legacyvault.entity.DmsConfirmer;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.DmsConfirmerDecision;
import com.ltld.app.legacyvault.enums.DmsConfirmerStatus;
import com.ltld.app.legacyvault.enums.DmsStatus;
import com.ltld.app.legacyvault.exception.DmsException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
import com.ltld.app.legacyvault.repository.DmsConfirmerRepository;
import com.ltld.app.legacyvault.security.JwtConfig;
import com.ltld.app.legacyvault.security.JwtProperties;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.utility.EmailSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
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
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
public class DmsConfirmerServiceImpl implements DmsConfirmerService {

    public static final int MAX_CONFIRMERS = 2;
    public static final int CONFIRMATION_TIMEOUT_HOURS = 72;
    public static final int POSTPONE_SAFE_DAYS = 7;

    public static final String PURPOSE_CLAIM = "purpose";
    public static final String PURPOSE_DMS_CONFIRM = "DMS_CONFIRM";
    public static final String DECISION_CLAIM = "decision";
    public static final Duration TOKEN_TTL = Duration.ofHours(CONFIRMATION_TIMEOUT_HOURS);
    // X2 (giống F2): audience riêng, KHÔNG chứa JwtConfig.AUDIENCE -> token xác nhận không dùng được như access token.
    public static final String CONFIRM_AUDIENCE = "legacyvault-dms-confirm";

    private final DmsConfigRepository dmsConfigRepository;
    private final DmsConfirmerRepository dmsConfirmerRepository;
    private final DmsActivationProcessor dmsActivationProcessor;
    private final JwtEncoder jwtEncoder;
    // Decoder riêng (không phải bean JwtDecoder chung): chỉ nhận audience CONFIRM_AUDIENCE, nên access token bị từ chối.
    private final JwtDecoder confirmTokenDecoder;
    private final EmailSender emailSender;
    private final AuditLogService auditLogService;
    private final String backendBaseUrl;
    private final String frontendBaseUrl;
    private final DmsScheduleCalculator scheduleCalculator = new DmsScheduleCalculator();

    public DmsConfirmerServiceImpl(
            DmsConfigRepository dmsConfigRepository,
            DmsConfirmerRepository dmsConfirmerRepository,
            @Lazy DmsActivationProcessor dmsActivationProcessor,
            JwtEncoder jwtEncoder,
            JwtProperties jwtProperties,
            EmailSender emailSender,
            AuditLogService auditLogService,
            @Value("${app.backend.base-url:${BACKEND_BASE_URL:}}") String backendBaseUrl,
            @Value("${app.frontend.base-url:${FRONTEND_BASE_URL:http://localhost:5173}}") String frontendBaseUrl
    ) {
        this.dmsConfigRepository = dmsConfigRepository;
        this.dmsConfirmerRepository = dmsConfirmerRepository;
        this.dmsActivationProcessor = dmsActivationProcessor;
        this.jwtEncoder = jwtEncoder;
        this.confirmTokenDecoder = buildConfirmTokenDecoder(jwtProperties.getSecret());
        this.emailSender = emailSender;
        this.auditLogService = auditLogService;
        this.frontendBaseUrl = normalizeBaseUrl(frontendBaseUrl, "http://localhost:5173");
        this.backendBaseUrl = resolveBackendBaseUrl(backendBaseUrl, this.frontendBaseUrl);
    }

    // Cùng khoá HS256 với JwtConfig (security.jwt.secret, UTF-8), nhưng validator đòi issuer JwtConfig.ISSUER
    // và audience CONFIRM_AUDIENCE; token có JwtConfig.AUDIENCE (access token) bị từ chối.
    private static JwtDecoder buildConfirmTokenDecoder(String secret) {
        SecretKeySpec key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        OAuth2TokenValidator<Jwt> issuer = JwtValidators.createDefaultWithIssuer(JwtConfig.ISSUER);
        OAuth2TokenValidator<Jwt> audience = jwt -> {
            List<String> aud = jwt.getAudience();
            return aud != null && aud.contains(CONFIRM_AUDIENCE) && !aud.contains(JwtConfig.AUDIENCE)
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Invalid audience for DMS confirmation token", null));
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
    @Transactional(readOnly = true)
    public List<DmsConfirmerResponse> listConfirmers(UUID ownerId) {
        return dmsConfigRepository.findByOwnerId(ownerId)
                .map(cfg -> dmsConfirmerRepository.findAllByDmsConfigIdOrderByCreatedAtAsc(cfg.getId())
                        .stream()
                        .map(this::toResponse)
                        .toList())
                .orElse(List.of());
    }

    @Override
    @Transactional
    public DmsConfirmerResponse addConfirmer(UUID ownerId, DmsConfirmerRequest request) {
        DmsConfig config = dmsConfigRepository.findByOwnerIdForUpdate(ownerId)
                .orElseThrow(() -> new DmsException(
                        "Configure and enable the Dead Man's Switch before adding trusted confirmers.",
                        HttpStatus.CONFLICT));

        if (config.getStatus() == DmsStatus.TRIGGERED) {
            throw new DmsException("The Dead Man's Switch has already been triggered.", HttpStatus.CONFLICT);
        }

        String normalizedEmail = request.getEmail().trim().toLowerCase(Locale.ROOT);
        User owner = config.getOwner();
        if (owner != null && owner.getEmail() != null && owner.getEmail().trim().equalsIgnoreCase(normalizedEmail)) {
            throw new DmsException(ErrorCode.BENEFICIARY_SELF, "You cannot assign yourself as a trusted confirmer.");
        }

        long currentCount = dmsConfirmerRepository.countByDmsConfigId(config.getId());
        if (currentCount >= MAX_CONFIRMERS) {
            throw new DmsException("You can configure at most " + MAX_CONFIRMERS + " trusted social confirmers.",
                    HttpStatus.CONFLICT);
        }

        if (dmsConfirmerRepository.existsByDmsConfigIdAndEmailIgnoreCase(config.getId(), normalizedEmail)) {
            throw new DmsException("This email is already added as a trusted confirmer.", HttpStatus.CONFLICT);
        }

        DmsConfirmer confirmer = DmsConfirmer.builder()
                .dmsConfig(config)
                .name(request.getName().trim())
                .email(normalizedEmail)
                .relationship(request.getRelationship() != null ? request.getRelationship().trim() : null)
                .status(DmsConfirmerStatus.ACTIVE)
                .build();

        confirmer = dmsConfirmerRepository.saveAndFlush(confirmer);

        auditLogService.log(
                AuditAction.DMS_CONFIRMER_ADDED,
                AuditResult.SUCCESS,
                ownerId,
                owner != null ? owner.getEmail() : null,
                null,
                DmsActivationProcessor.ENTITY_DMS_CONFIG,
                config.getId().toString(),
                DmsActivationProcessor.ownerDetail(ownerId) + ", confirmerId=" + confirmer.getId()
        );

        return toResponse(confirmer);
    }

    @Override
    @Transactional
    public void removeConfirmer(UUID ownerId, UUID confirmerId) {
        DmsConfig config = dmsConfigRepository.findByOwnerIdForUpdate(ownerId)
                .orElseThrow(() -> new DmsException("Dead Man's Switch is not configured.", HttpStatus.NOT_FOUND));

        if (config.getStatus() == DmsStatus.TRIGGERED) {
            throw new DmsException("The Dead Man's Switch has already been triggered.", HttpStatus.CONFLICT);
        }

        DmsConfirmer confirmer = dmsConfirmerRepository.findByIdAndDmsConfigId(confirmerId, config.getId())
                .orElseThrow(() -> new DmsException("Trusted confirmer not found.", HttpStatus.NOT_FOUND));

        dmsConfirmerRepository.delete(confirmer);
        dmsConfirmerRepository.flush();

        User owner = config.getOwner();
        auditLogService.log(
                AuditAction.DMS_CONFIRMER_REMOVED,
                AuditResult.SUCCESS,
                ownerId,
                owner != null ? owner.getEmail() : null,
                null,
                DmsActivationProcessor.ENTITY_DMS_CONFIG,
                config.getId().toString(),
                DmsActivationProcessor.ownerDetail(ownerId) + ", confirmerId=" + confirmerId
        );
    }

    @Override
    public String generateConfirmationToken(UUID confirmerId, DmsConfirmerDecision decision) {
        Instant now = Instant.now();
        String jti = UUID.randomUUID().toString();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtConfig.ISSUER)
                .audience(List.of(CONFIRM_AUDIENCE))
                .subject(confirmerId.toString())
                .id(jti)
                .issuedAt(now)
                .expiresAt(now.plus(TOKEN_TTL))
                .claim(PURPOSE_CLAIM, PURPOSE_DMS_CONFIRM)
                .claim(DECISION_CLAIM, decision.name())
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    @Override
    @Transactional
    public boolean initiatePendingConfirmationIfConfigured(DmsConfig config, LocalDateTime now) {
        List<DmsConfirmer> confirmers = dmsConfirmerRepository.findAllByDmsConfigIdOrderByCreatedAtAsc(config.getId());
        if (confirmers == null || confirmers.isEmpty()) {
            return false;
        }

        LocalDateTime deadlineAt = now.plusHours(CONFIRMATION_TIMEOUT_HOURS);
        config.setStatus(DmsStatus.PENDING_CONFIRMATION);
        config.setNextCheckinDueAt(deadlineAt);
        config.setNextReminderAt(null);
        dmsConfigRepository.saveAndFlush(config);

        for (DmsConfirmer confirmer : confirmers) {
            confirmer.setStatus(DmsConfirmerStatus.ACTIVE);
            confirmer.setRespondedAt(null);
            dmsConfirmerRepository.save(confirmer);
        }
        dmsConfirmerRepository.flush();

        UUID ownerId = config.getOwner().getId();
        auditLogService.log(
                AuditAction.DMS_CONFIRMATION_REQUESTED,
                AuditResult.SUCCESS,
                null,
                null,
                null,
                DmsActivationProcessor.ENTITY_DMS_CONFIG,
                config.getId().toString(),
                DmsActivationProcessor.ownerDetail(ownerId) + ", confirmers=" + confirmers.size()
                        + ", deadlineAt=" + deadlineAt
        );

        String base = normalizeBaseUrl(backendBaseUrl, "http://localhost:8084");
        User owner = config.getOwner();
        for (DmsConfirmer confirmer : confirmers) {
            String aliveToken = generateConfirmationToken(confirmer.getId(), DmsConfirmerDecision.ALIVE);
            String unreachableToken = generateConfirmationToken(confirmer.getId(), DmsConfirmerDecision.UNREACHABLE);
            String aliveUrl = base + "/api/dms/confirm?token="
                    + URLEncoder.encode(aliveToken, StandardCharsets.UTF_8) + "&decision=ALIVE";
            String unreachableUrl = base + "/api/dms/confirm?token="
                    + URLEncoder.encode(unreachableToken, StandardCharsets.UTF_8) + "&decision=UNREACHABLE";
            try {
                emailSender.sendDmsCrossConfirmationRequest(
                        confirmer.getEmail(),
                        confirmer.getName(),
                        owner.getFullName(),
                        owner.getEmail(),
                        deadlineAt,
                        aliveUrl,
                        unreachableUrl
                );
            } catch (RuntimeException ex) {
                log.warn("X2: failed to send cross-confirmation email to {} for dmsConfig {}: {}",
                        confirmer.getEmail(), config.getId(), ex.getMessage());
            }
        }
        return true;
    }

    @Override
    @Transactional
    public DmsConfirmationDecisionResponse respondByToken(
            String rawToken,
            DmsConfirmerDecision decisionParam,
            LocalDateTime now
    ) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new DmsException(ErrorCode.CONFIRMER_TOKEN_INVALID, "Confirmation token is missing.");
        }

        Jwt jwt;
        try {
            jwt = confirmTokenDecoder.decode(rawToken.trim());
        } catch (JwtException | IllegalArgumentException ex) {
            throw new DmsException(ErrorCode.CONFIRMER_TOKEN_INVALID, "Confirmation token is invalid or expired.");
        }

        if (!PURPOSE_DMS_CONFIRM.equals(jwt.getClaimAsString(PURPOSE_CLAIM))) {
            throw new DmsException(ErrorCode.CONFIRMER_TOKEN_INVALID, "Invalid token purpose for DMS confirmation.");
        }

        Instant expiresAt = jwt.getExpiresAt();
        if (expiresAt == null || !Instant.now().isBefore(expiresAt)) {
            throw new DmsException(ErrorCode.CONFIRMER_TOKEN_INVALID, "Confirmation token has expired.");
        }

        String jti = jwt.getId();
        if (jti == null || jti.isBlank()) {
            throw new DmsException(ErrorCode.CONFIRMER_TOKEN_INVALID, "Confirmation token is missing jti.");
        }

        if (dmsConfirmerRepository.existsByLastTokenJti(jti)) {
            throw new DmsException(ErrorCode.CONFIRMER_TOKEN_INVALID, "This confirmation link has already been used.");
        }

        UUID confirmerId;
        try {
            confirmerId = UUID.fromString(jwt.getSubject());
        } catch (RuntimeException ex) {
            throw new DmsException(ErrorCode.CONFIRMER_TOKEN_INVALID, "Confirmation token subject is invalid.");
        }

        DmsConfirmerDecision tokenDecision = parseDecision(jwt.getClaimAsString(DECISION_CLAIM));
        DmsConfirmerDecision effectiveDecision = decisionParam != null ? decisionParam : tokenDecision;
        if (effectiveDecision == null) {
            throw new DmsException(ErrorCode.CONFIRMER_TOKEN_INVALID, "Confirmation decision (ALIVE or UNREACHABLE) is required.");
        }

        DmsConfirmer confirmer = dmsConfirmerRepository.findById(confirmerId)
                .orElseThrow(() -> new DmsException(ErrorCode.CONFIRMER_TOKEN_INVALID, "Trusted confirmer record no longer exists."));

        DmsConfig config = dmsConfigRepository.findByOwnerIdForUpdate(confirmer.getDmsConfig().getOwner().getId())
                .orElseThrow(() -> new DmsException(ErrorCode.CONFIRMER_TOKEN_INVALID, "Dead Man's Switch is not configured."));

        if (config.getStatus() != DmsStatus.PENDING_CONFIRMATION) {
            throw new DmsException(ErrorCode.CONFIRMER_TOKEN_INVALID, "This Dead Man's Switch is not awaiting confirmation.");
        }

        LocalDateTime effectiveNow = now != null ? now : LocalDateTime.now();
        confirmer.setLastTokenJti(jti);
        confirmer.setRespondedAt(effectiveNow);

        User owner = config.getOwner();
        UUID ownerId = owner.getId();

        if (effectiveDecision == DmsConfirmerDecision.ALIVE) {
            confirmer.setStatus(DmsConfirmerStatus.CONFIRMED_ALIVE);
            LocalDateTime postponedUntil = effectiveNow.plusDays(POSTPONE_SAFE_DAYS);
            config.setStatus(DmsStatus.ACTIVE);
            startPostponedCycle(config, postponedUntil);

            try {
                dmsConfirmerRepository.saveAndFlush(confirmer);
                dmsConfigRepository.saveAndFlush(config);
            } catch (DataIntegrityViolationException ex) {
                throw new DmsException(ErrorCode.CONFIRMER_TOKEN_INVALID, "This confirmation link has already been used.");
            }

            auditLogService.log(
                    AuditAction.DMS_CONFIRMED_ALIVE,
                    AuditResult.SUCCESS,
                    null,
                    null,
                    null,
                    DmsActivationProcessor.ENTITY_DMS_CONFIG,
                    config.getId().toString(),
                    DmsActivationProcessor.ownerDetail(ownerId) + ", confirmerId=" + confirmer.getId()
                            + ", postponedUntil=" + postponedUntil
            );

            try {
                emailSender.sendOwnerConfirmedSafeNotice(owner.getEmail(), confirmer.getName(), postponedUntil);
            } catch (RuntimeException ex) {
                log.warn("X2: could not send owner safe notice for dmsConfig {}: {}", config.getId(), ex.getMessage());
            }

            return DmsConfirmationDecisionResponse.builder()
                    .dmsStatus(DmsStatus.ACTIVE)
                    .decision(DmsConfirmerDecision.ALIVE)
                    .ownerName(owner.getFullName() != null ? owner.getFullName() : owner.getEmail())
                    .postponedUntil(postponedUntil)
                    .message("Confirmed owner is safe. Dead Man's Switch postponed by 7 days.")
                    .build();
        }

        // UNREACHABLE -> kích hoạt ngay lập tức
        confirmer.setStatus(DmsConfirmerStatus.CONFIRMED_UNREACHABLE);
        try {
            dmsConfirmerRepository.saveAndFlush(confirmer);
        } catch (DataIntegrityViolationException ex) {
            throw new DmsException(ErrorCode.CONFIRMER_TOKEN_INVALID, "This confirmation link has already been used.");
        }

        auditLogService.log(
                AuditAction.DMS_CONFIRMED_UNREACHABLE,
                AuditResult.SUCCESS,
                null,
                null,
                null,
                DmsActivationProcessor.ENTITY_DMS_CONFIG,
                config.getId().toString(),
                DmsActivationProcessor.ownerDetail(ownerId) + ", confirmerId=" + confirmer.getId()
        );

        dmsActivationProcessor.activateConfirmedDms(config, effectiveNow);

        return DmsConfirmationDecisionResponse.builder()
                .dmsStatus(DmsStatus.TRIGGERED)
                .decision(DmsConfirmerDecision.UNREACHABLE)
                .ownerName(owner.getFullName() != null ? owner.getFullName() : owner.getEmail())
                .triggeredAt(effectiveNow)
                .message("Confirmed owner is unreachable. Dead Man's Switch triggered and executor notified.")
                .build();
    }

    @Override
    @Transactional
    public String respondAndBuildRedirectUrl(String rawToken, String decisionParam) {
        DmsConfirmerDecision decision = parseDecision(decisionParam);
        DmsConfirmationDecisionResponse res = respondByToken(rawToken, decision, LocalDateTime.now());
        String base = normalizeBaseUrl(frontendBaseUrl, "http://localhost:5173");
        return base + "/dms/confirm-done?status=success&decision=" + res.getDecision().name()
                + "&dmsStatus=" + res.getDmsStatus().name();
    }

    @Override
    public String buildErrorRedirectUrl(ErrorCode errorCode) {
        ErrorCode code = errorCode != null ? errorCode : ErrorCode.INTERNAL_ERROR;
        String base = normalizeBaseUrl(frontendBaseUrl, "http://localhost:5173");
        return base + "/dms/confirm-done?status=error&errorCode=" + code.name();
    }

    /**
     * ALIVE: hạn mới là postponedUntil và Owner phải được nhắc lại theo đúng lịch nhắc như sau check-in.
     * Dùng chung DmsScheduleCalculator và cùng các trường như DmsConfigServiceImpl.startNewCycle, chỉ khác mốc:
     * chu kỳ được đặt để triggerAt rơi đúng postponedUntil, nên các lần nhắc chia đều thời gian chờ ngay trước hạn.
     * DmsReminderProcessor tính lại lịch từ lastCheckinAt nên lastCheckinAt phải là mốc chu kỳ này.
     * remindersSent về 0: nhắc lần lượt rồi dừng (nextReminderAt = null sau lần cuối), không nhắc lặp vô hạn.
     */
    private void startPostponedCycle(DmsConfig config, LocalDateTime postponedUntil) {
        Integer interval = config.getCheckinIntervalDays();
        Integer grace = config.getGracePeriodDays();
        Integer reminders = config.getReminderCount();
        if (interval == null || grace == null || reminders == null) {
            // Lịch chưa đủ (không xảy ra với cấu hình đã lưu qua upsertConfig): chỉ lùi hạn như trước.
            config.setNextCheckinDueAt(postponedUntil);
            return;
        }
        LocalDateTime cycleStart = postponedUntil.minusDays((long) interval + grace);
        DmsSchedule schedule = scheduleCalculator.calculate(cycleStart, interval, grace, reminders);
        config.setLastCheckinAt(cycleStart);
        config.setRemindersSent(0);
        config.setTriggerDeferrals(0);
        config.setNextCheckinDueAt(schedule.triggerAt());
        config.setNextReminderAt(schedule.nextReminderAt(0).orElse(null));
    }

    private static DmsConfirmerDecision parseDecision(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return DmsConfirmerDecision.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new DmsException(ErrorCode.CONFIRMER_TOKEN_INVALID, "Invalid confirmation decision (must be ALIVE or UNREACHABLE).");
        }
    }

    private DmsConfirmerResponse toResponse(DmsConfirmer c) {
        return DmsConfirmerResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .email(c.getEmail())
                .relationship(c.getRelationship())
                .status(c.getStatus())
                .respondedAt(c.getRespondedAt())
                .createdAt(c.getCreatedAt())
                .build();
    }

    private static String normalizeBaseUrl(String url, String fallback) {
        String raw = (url == null || url.isBlank()) ? fallback : url.trim();
        return raw.endsWith("/") ? raw.substring(0, raw.length() - 1) : raw;
    }
}
