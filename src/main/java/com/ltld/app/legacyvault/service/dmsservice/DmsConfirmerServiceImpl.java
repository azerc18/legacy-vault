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
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import com.ltld.app.legacyvault.security.JwtConfig;
import com.ltld.app.legacyvault.security.JwtProperties;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.paymentservice.EntitlementService;
import com.ltld.app.legacyvault.utility.EmailSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
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
    // C1/M1: token không mang quyết định; claim round = hạn chờ của đợt (epoch giây, UTC) để token chỉ dùng trong đợt đó.
    public static final String ROUND_CLAIM = "round";
    public static final Duration TOKEN_TTL = Duration.ofHours(CONFIRMATION_TIMEOUT_HOURS);
    // X2 (giống F2): audience riêng, KHÔNG chứa JwtConfig.AUDIENCE -> token xác nhận không dùng được như access token.
    public static final String CONFIRM_AUDIENCE = "legacyvault-dms-confirm";

    private final DmsConfigRepository dmsConfigRepository;
    private final DmsConfirmerRepository dmsConfirmerRepository;
    private final ExecutorAssignmentRepository executorAssignmentRepository;
    private final DmsActivationProcessor dmsActivationProcessor;
    private final EntitlementService entitlementService;
    private final JwtEncoder jwtEncoder;
    // Decoder riêng (không phải bean JwtDecoder chung): chỉ nhận audience CONFIRM_AUDIENCE, nên access token bị từ chối.
    private final JwtDecoder confirmTokenDecoder;
    private final EmailSender emailSender;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;
    private final DmsScheduleCalculator scheduleCalculator = new DmsScheduleCalculator();

    public DmsConfirmerServiceImpl(
            DmsConfigRepository dmsConfigRepository,
            DmsConfirmerRepository dmsConfirmerRepository,
            ExecutorAssignmentRepository executorAssignmentRepository,
            @Lazy DmsActivationProcessor dmsActivationProcessor,
            EntitlementService entitlementService,
            JwtEncoder jwtEncoder,
            JwtProperties jwtProperties,
            EmailSender emailSender,
            AuditLogService auditLogService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.dmsConfigRepository = dmsConfigRepository;
        this.dmsConfirmerRepository = dmsConfirmerRepository;
        this.executorAssignmentRepository = executorAssignmentRepository;
        this.dmsActivationProcessor = dmsActivationProcessor;
        this.entitlementService = entitlementService;
        this.jwtEncoder = jwtEncoder;
        this.confirmTokenDecoder = buildConfirmTokenDecoder(jwtProperties.getSecret());
        this.emailSender = emailSender;
        this.auditLogService = auditLogService;
        this.eventPublisher = eventPublisher;
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
        // m4: mọi lỗi dùng ErrorCode có sẵn đúng nghĩa (CONFLICT / BAD_REQUEST); n7: trùng email dùng CONFLICT để FE hiện câu cụ thể của BE
        DmsConfig config = dmsConfigRepository.findByOwnerIdForUpdate(ownerId)
                .orElseThrow(() -> new DmsException(ErrorCode.CONFLICT,
                        "Configure and enable the Dead Man's Switch before adding trusted confirmers."));

        // M2: tính năng VIP; FREE -> 403 PLAN_LIMIT_REACHED (feature=TRUSTED_CONFIRMERS). Kiểm sau khi khoá config.
        // Không chặn ở initiatePendingConfirmationIfConfigured: VIP hết hạn thì confirmer đã khai vẫn được hỏi.
        entitlementService.assertCanUseTrustedConfirmers(ownerId);

        if (config.getStatus() == DmsStatus.TRIGGERED) {
            throw new DmsException(ErrorCode.CONFLICT, "The Dead Man's Switch has already been triggered.");
        }

        String normalizedEmail = request.getEmail().trim().toLowerCase(Locale.ROOT);
        User owner = config.getOwner();
        if (owner != null && owner.getEmail() != null && owner.getEmail().trim().equalsIgnoreCase(normalizedEmail)) {
            throw new DmsException(ErrorCode.BAD_REQUEST, "You cannot assign yourself as a trusted confirmer.");
        }

        // m1: Executor còn hiệu lực (Primary hoặc Backup) của Owner không được làm người xác nhận chéo,
        // vì bấm UNREACHABLE sẽ kích hoạt chính mình. Chiều ngược lại chặn ở ExecutorManagementServiceImpl.
        if (executorAssignmentRepository.existsLiveExecutorWithEmail(ownerId, normalizedEmail)) {
            throw new DmsException(ErrorCode.CONFLICT, "Your executor cannot also be a trusted confirmer.");
        }

        long currentCount = dmsConfirmerRepository.countByDmsConfigId(config.getId());
        if (currentCount >= MAX_CONFIRMERS) {
            throw new DmsException(ErrorCode.CONFLICT,
                    "You can configure at most " + MAX_CONFIRMERS + " trusted social confirmers.");
        }

        if (dmsConfirmerRepository.existsByDmsConfigIdAndEmailIgnoreCase(config.getId(), normalizedEmail)) {
            throw new DmsException(ErrorCode.CONFLICT, "This email is already added as a trusted confirmer.");
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
                .orElseThrow(() -> new DmsException(ErrorCode.NOT_FOUND, "Dead Man's Switch is not configured."));

        if (config.getStatus() == DmsStatus.TRIGGERED) {
            throw new DmsException(ErrorCode.CONFLICT, "The Dead Man's Switch has already been triggered.");
        }

        DmsConfirmer confirmer = dmsConfirmerRepository.findByIdAndDmsConfigId(confirmerId, config.getId())
                .orElseThrow(() -> new DmsException(ErrorCode.NOT_FOUND, "Trusted confirmer not found."));

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
    public String generateConfirmationToken(UUID confirmerId, LocalDateTime deadlineAt) {
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
                .claim(ROUND_CLAIM, roundOf(deadlineAt))
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

        // DATETIME(6): cắt về micro giây để giá trị đọc lại từ DB trùng giá trị trong bộ nhớ (so sánh đợt).
        LocalDateTime deadlineAt = now.plusHours(CONFIRMATION_TIMEOUT_HOURS).truncatedTo(ChronoUnit.MICROS);
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

        // M3: không gửi SMTP trong transaction đang khoá DmsConfig; listener gửi sau commit (rollback thì không gửi).
        eventPublisher.publishEvent(new DmsConfirmationRequestedEvent(config.getId(), deadlineAt));
        return true;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ConfirmationMails prepareConfirmationMails(UUID configId, LocalDateTime deadlineAt) {
        DmsConfig config = dmsConfigRepository.findById(configId).orElse(null);
        if (config == null || config.getStatus() != DmsStatus.PENDING_CONFIRMATION
                || !sameRound(config.getNextCheckinDueAt(), deadlineAt)) {
            return null;
        }
        List<ConfirmerMail> mails = dmsConfirmerRepository.findAllByDmsConfigIdOrderByCreatedAtAsc(configId).stream()
                .map(c -> new ConfirmerMail(c.getEmail(), c.getName(), generateConfirmationToken(c.getId(), deadlineAt)))
                .toList();
        User owner = config.getOwner();
        return new ConfirmationMails(owner.getEmail(), owner.getFullName(), deadlineAt, mails);
    }

    @Override
    @Transactional
    public DmsConfirmationDecisionResponse respondByToken(
            String rawToken,
            DmsConfirmerDecision decision,
            LocalDateTime now
    ) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new DmsException(ErrorCode.CONFIRMER_TOKEN_INVALID, "Confirmation token is missing.");
        }
        // M1: quyết định chỉ lấy từ body request (người bấm nút), không từ token hay URL.
        if (decision == null) {
            throw new DmsException(ErrorCode.MISSING_PARAMETER, "Confirmation decision (ALIVE or UNREACHABLE) is required.");
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

        UUID confirmerId;
        try {
            confirmerId = UUID.fromString(jwt.getSubject());
        } catch (RuntimeException ex) {
            throw new DmsException(ErrorCode.CONFIRMER_TOKEN_INVALID, "Confirmation token subject is invalid.");
        }

        Long round = roundClaim(jwt);

        DmsConfirmer confirmer = dmsConfirmerRepository.findById(confirmerId)
                .orElseThrow(() -> new DmsException(ErrorCode.CONFIRMER_TOKEN_INVALID, "Trusted confirmer record no longer exists."));

        DmsConfig config = dmsConfigRepository.findByOwnerIdForUpdate(confirmer.getDmsConfig().getOwner().getId())
                .orElseThrow(() -> new DmsException(ErrorCode.CONFIRMER_TOKEN_INVALID, "Dead Man's Switch is not configured."));

        // m7: kiểm jti SAU khi khoá dòng DmsConfig, để hai request cùng token không cùng lọt qua bước kiểm.
        if (dmsConfirmerRepository.existsByLastTokenJti(jti)) {
            throw new DmsException(ErrorCode.CONFIRMER_TOKEN_INVALID, "This confirmation link has already been used.");
        }

        if (config.getStatus() != DmsStatus.PENDING_CONFIRMATION) {
            throw new DmsException(ErrorCode.CONFIRMER_TOKEN_INVALID, "This Dead Man's Switch is not awaiting confirmation.");
        }
        // Mỗi confirmer mỗi đợt một token: link của đợt trước không dùng được cho đợt hiện tại.
        if (round == null || config.getNextCheckinDueAt() == null || round != roundOf(config.getNextCheckinDueAt())) {
            throw new DmsException(ErrorCode.CONFIRMER_TOKEN_INVALID, "This confirmation link belongs to an earlier request.");
        }

        LocalDateTime effectiveNow = now != null ? now : LocalDateTime.now();
        confirmer.setLastTokenJti(jti);
        confirmer.setRespondedAt(effectiveNow);

        User owner = config.getOwner();
        UUID ownerId = owner.getId();
        String ownerName = owner.getFullName() != null ? owner.getFullName() : owner.getEmail();

        if (decision == DmsConfirmerDecision.ALIVE) {
            confirmer.setStatus(DmsConfirmerStatus.CONFIRMED_ALIVE);
            LocalDateTime postponedUntil = effectiveNow.plusDays(POSTPONE_SAFE_DAYS);
            config.setStatus(DmsStatus.ACTIVE);
            // m2: ALIVE chỉ 1 lần/đợt; đợt quá hạn kế tiếp mà Owner chưa check-in thì kích hoạt luôn.
            config.setConfirmationDeferrals(config.getConfirmationDeferrals() + 1);
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

            String ownerEmail = owner.getEmail();
            String confirmerName = confirmer.getName();
            UUID configId = config.getId();
            runAfterCommit(() -> {
                try {
                    emailSender.sendOwnerConfirmedSafeNotice(ownerEmail, confirmerName, postponedUntil);
                } catch (RuntimeException ex) {
                    log.warn("X2: could not send owner safe notice for dmsConfig {}: {}", configId, ex.getMessage());
                }
            });

            return DmsConfirmationDecisionResponse.builder()
                    .dmsStatus(DmsStatus.ACTIVE)
                    .decision(DmsConfirmerDecision.ALIVE)
                    .ownerName(ownerName)
                    .postponedUntil(postponedUntil)
                    .message("Confirmed owner is safe. Dead Man's Switch postponed by 7 days.")
                    .build();
        }

        // UNREACHABLE -> kích hoạt ngay lập tức (một người là đủ: Owner đã quá hạn và hết thời gian ân hạn)
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
                .ownerName(ownerName)
                .triggeredAt(effectiveNow)
                .message("Confirmed owner is unreachable. Dead Man's Switch triggered and executor notified.")
                .build();
    }

    /**
     * ALIVE: hạn mới là postponedUntil và Owner phải được nhắc lại theo đúng lịch nhắc như sau check-in.
     * m3: chỉ dời cycle_started_at (mốc lịch nhắc), KHÔNG đụng lastCheckinAt: lastCheckinAt luôn là lần check-in thật
     * (trang DMS / Admin / F1 hiển thị và dùng nó). Mốc chu kỳ đặt để triggerAt rơi đúng postponedUntil, nên các lần
     * nhắc chia đều thời gian chờ ngay trước hạn. remindersSent về 0: nhắc lần lượt rồi dừng.
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
        config.setCycleStartedAt(cycleStart);
        config.setRemindersSent(0);
        config.setTriggerDeferrals(0);
        config.setNextCheckinDueAt(schedule.triggerAt());
        config.setNextReminderAt(schedule.nextReminderAt(0).orElse(null));
    }

    // Đợt xác nhận nhận diện bằng hạn chờ (cắt về giây, UTC) để không lệch vì độ chính xác DATETIME(6).
    static long roundOf(LocalDateTime deadlineAt) {
        return deadlineAt.truncatedTo(ChronoUnit.SECONDS).toEpochSecond(ZoneOffset.UTC);
    }

    private static boolean sameRound(LocalDateTime a, LocalDateTime b) {
        return a != null && b != null && roundOf(a) == roundOf(b);
    }

    private static Long roundClaim(Jwt jwt) {
        Object raw = jwt.getClaims().get(ROUND_CLAIM);
        if (raw instanceof Number n) {
            return n.longValue();
        }
        if (raw instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static void runAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
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
}
