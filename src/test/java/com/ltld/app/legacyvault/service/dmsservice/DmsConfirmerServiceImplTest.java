package com.ltld.app.legacyvault.service.dmsservice;

import com.ltld.app.legacyvault.dto.dmsdto.DmsConfirmationDecisionResponse;
import com.ltld.app.legacyvault.dto.dmsdto.DmsConfirmerRequest;
import com.ltld.app.legacyvault.entity.DmsConfig;
import com.ltld.app.legacyvault.entity.DmsConfirmer;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.Role;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.DmsConfirmerDecision;
import com.ltld.app.legacyvault.enums.DmsConfirmerStatus;
import com.ltld.app.legacyvault.enums.DmsStatus;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.ExecutorType;
import com.ltld.app.legacyvault.enums.PlanFeature;
import com.ltld.app.legacyvault.exception.BusinessException;
import com.ltld.app.legacyvault.exception.DmsException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.exception.PlanLimitReachedException;
import com.ltld.app.legacyvault.repository.DmsCheckinRepository;
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
import com.ltld.app.legacyvault.repository.DmsConfirmerRepository;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import com.ltld.app.legacyvault.repository.NotificationRepository;
import com.ltld.app.legacyvault.repository.RoleRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.security.JwtConfig;
import com.ltld.app.legacyvault.security.JwtProperties;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.executorservice.ExecutorRoleManager;
import com.ltld.app.legacyvault.service.notificationservice.ExecutorNotificationService;
import com.ltld.app.legacyvault.service.paymentservice.EntitlementService;
import com.ltld.app.legacyvault.utility.EmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DmsConfirmerServiceImplTest {

    @Mock private DmsConfigRepository dmsConfigRepository;
    @Mock private DmsConfirmerRepository dmsConfirmerRepository;
    @Mock private DmsCheckinRepository dmsCheckinRepository;
    @Mock private ExecutorAssignmentRepository executorAssignmentRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private ExecutorNotificationService executorNotificationService;
    @Mock private UserRepository userRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private EmailSender emailSender;
    @Mock private EntitlementService entitlementService;
    @Mock private VaultRepository vaultRepository;

    private DmsActivationProcessor processor;
    private DmsConfirmerServiceImpl confirmerService;

    private User owner;
    private DmsConfig config;
    private DmsConfirmer confirmer;
    private ExecutorAssignment primary;
    private LocalDateTime now;
    private LocalDateTime lastCheckin;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret("0123456789abcdef0123456789abcdef0123456789abcdef");
        JwtConfig jwtConfig = new JwtConfig(props);
        JwtEncoder jwtEncoder = jwtConfig.jwtEncoder();

        // m6: vòng phụ thuộc processor <-> confirmerService nối qua constructor; ở đây dùng một holder giống @Lazy.
        DmsConfirmerService lazyConfirmer = mock(DmsConfirmerService.class, inv -> inv.getMethod().invoke(confirmerService, inv.getArguments()));
        processor = new DmsActivationProcessor(
                dmsConfigRepository,
                dmsCheckinRepository,
                executorAssignmentRepository,
                notificationRepository,
                new ExecutorRoleManager(roleRepository, executorAssignmentRepository),
                auditLogService,
                executorNotificationService,
                userRepository,
                eventPublisher,
                lazyConfirmer,
                vaultRepository
        );
        when(vaultRepository.existsByOwnerId(any())).thenReturn(true);

        confirmerService = new DmsConfirmerServiceImpl(
                dmsConfigRepository,
                dmsConfirmerRepository,
                executorAssignmentRepository,
                processor,
                entitlementService,
                jwtEncoder,
                props,
                emailSender,
                auditLogService,
                eventPublisher
        );

        now = LocalDateTime.of(2026, 10, 31, 12, 0);
        lastCheckin = now.minusDays(38);
        owner = User.builder()
                .id(UUID.randomUUID())
                .fullName("Nguyễn Văn A")
                .email("owner@example.com")
                .build();

        config = DmsConfig.builder()
                .id(UUID.randomUUID())
                .owner(owner)
                .status(DmsStatus.ACTIVE)
                .checkinIntervalDays(30)
                .gracePeriodDays(7)
                .reminderCount(3)
                .remindersSent(3)
                .lastCheckinAt(lastCheckin)
                .cycleStartedAt(lastCheckin)
                .nextCheckinDueAt(now.minusHours(1))
                .backupActivationWaitDays(14)
                .build();

        confirmer = DmsConfirmer.builder()
                .id(UUID.randomUUID())
                .dmsConfig(config)
                .name("Trần Thị B")
                .email("confirmer@example.com")
                .relationship("Spouse")
                .status(DmsConfirmerStatus.ACTIVE)
                .build();

        primary = ExecutorAssignment.builder()
                .id(UUID.randomUUID())
                .owner(owner)
                .executorUser(User.builder().id(UUID.randomUUID()).email("primary@example.com").build())
                .executorType(ExecutorType.PRIMARY)
                .status(ExecutorStatus.INACTIVE)
                .build();

        Role executorRole = new Role();
        executorRole.setCode("EXECUTOR");
        when(roleRepository.findByCode("EXECUTOR")).thenReturn(Optional.of(executorRole));
        when(dmsConfigRepository.findById(config.getId())).thenReturn(Optional.of(config));
        when(dmsConfigRepository.findByOwnerIdForUpdate(owner.getId())).thenReturn(Optional.of(config));
        when(executorAssignmentRepository.findByOwnerIdAndExecutorType(owner.getId(), ExecutorType.PRIMARY))
                .thenReturn(Optional.of(primary));
        when(dmsConfirmerRepository.findById(confirmer.getId())).thenReturn(Optional.of(confirmer));
        when(dmsConfirmerRepository.saveAndFlush(any(DmsConfirmer.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    /** Đưa config vào PENDING_CONFIRMATION như trigger thật, trả về hạn chờ của đợt. */
    private LocalDateTime enterPending() {
        when(dmsConfirmerRepository.findAllByDmsConfigIdOrderByCreatedAtAsc(config.getId())).thenReturn(List.of(confirmer));
        processor.trigger(config.getId(), now);
        assertEquals(DmsStatus.PENDING_CONFIRMATION, config.getStatus());
        return config.getNextCheckinDueAt();
    }

    private DmsConfirmerRequest request(String email) {
        return DmsConfirmerRequest.builder().name("Lê C").email(email).relationship("Friend").build();
    }

    // ---------- trigger ----------

    @Test
    @DisplayName("X2 M3: quá hạn khi có confirmer -> PENDING_CONFIRMATION 72h, phát event; KHÔNG gửi mail trong transaction")
    void trigger_withConfirmer_entersPending_publishesEvent_noMailInTransaction() {
        LocalDateTime deadline = enterPending();

        assertEquals(now.plusHours(72), deadline);
        assertEquals(ExecutorStatus.INACTIVE, primary.getStatus());
        verify(eventPublisher).publishEvent(new DmsConfirmationRequestedEvent(config.getId(), deadline));
        verifyNoInteractions(emailSender);
        verify(auditLogService).log(eq(AuditAction.DMS_CONFIRMATION_REQUESTED), eq(AuditResult.SUCCESS),
                isNull(), isNull(), isNull(), eq(DmsActivationProcessor.ENTITY_DMS_CONFIG),
                eq(config.getId().toString()), contains("confirmers=1"));
    }

    @Test
    @DisplayName("X2 m2: đợt này đã được ALIVE hoãn 1 lần mà Owner chưa check-in -> kích hoạt luôn, không hỏi lại")
    void trigger_afterOneAlive_triggersWithoutAskingAgain() {
        config.setConfirmationDeferrals(1);
        when(dmsConfirmerRepository.findAllByDmsConfigIdOrderByCreatedAtAsc(config.getId())).thenReturn(List.of(confirmer));

        processor.trigger(config.getId(), now);

        assertEquals(DmsStatus.TRIGGERED, config.getStatus());
        assertEquals(ExecutorStatus.ACTIVE, primary.getStatus());
        verify(eventPublisher, never()).publishEvent(any(DmsConfirmationRequestedEvent.class));
    }

    // ---------- respondByToken ----------

    @Test
    @DisplayName("X2 Nhánh 1 + m2/m3: ALIVE -> hoãn 7 ngày, ACTIVE, đếm ALIVE = 1, dời cycle_started_at, giữ lastCheckinAt thật")
    void respondByToken_alive_postpones_countsDeferral_keepsRealLastCheckin() {
        LocalDateTime deadline = enterPending();
        String token = confirmerService.generateConfirmationToken(confirmer.getId(), deadline);

        DmsConfirmationDecisionResponse res = confirmerService.respondByToken(token, DmsConfirmerDecision.ALIVE, now);

        assertEquals(DmsStatus.ACTIVE, res.getDmsStatus());
        assertEquals(DmsConfirmerDecision.ALIVE, res.getDecision());
        assertEquals(now.plusDays(7), config.getNextCheckinDueAt());
        assertEquals(1, config.getConfirmationDeferrals());
        assertEquals(lastCheckin, config.getLastCheckinAt());                  // m3: không giả lần check-in
        assertEquals(now.plusDays(7).minusDays(37), config.getCycleStartedAt());   // mốc lịch nhắc mới
        assertEquals(0, config.getRemindersSent());
        assertNotNull(config.getNextReminderAt());
        assertEquals(DmsConfirmerStatus.CONFIRMED_ALIVE, confirmer.getStatus());
        assertEquals(ExecutorStatus.INACTIVE, primary.getStatus());
        verify(emailSender).sendOwnerConfirmedSafeNotice("owner@example.com", "Trần Thị B", now.plusDays(7));
    }

    @Test
    @DisplayName("X2 Nhánh 2: UNREACHABLE -> lập tức TRIGGERED và kích hoạt Primary Executor")
    void respondByToken_unreachable_triggersImmediately() {
        LocalDateTime deadline = enterPending();
        String token = confirmerService.generateConfirmationToken(confirmer.getId(), deadline);

        DmsConfirmationDecisionResponse res = confirmerService.respondByToken(token, DmsConfirmerDecision.UNREACHABLE, now);

        assertEquals(DmsStatus.TRIGGERED, res.getDmsStatus());
        assertEquals(DmsStatus.TRIGGERED, config.getStatus());
        assertEquals(DmsConfirmerStatus.CONFIRMED_UNREACHABLE, confirmer.getStatus());
        assertEquals(ExecutorStatus.ACTIVE, primary.getStatus());
        verify(auditLogService).log(eq(AuditAction.DMS_CONFIRMED_UNREACHABLE), eq(AuditResult.SUCCESS),
                isNull(), isNull(), isNull(), eq(DmsActivationProcessor.ENTITY_DMS_CONFIG),
                eq(config.getId().toString()), contains("confirmerId="));
    }

    @Test
    @DisplayName("X2 M1: quyết định chỉ từ body; thiếu quyết định -> MISSING_PARAMETER, DMS giữ PENDING")
    void respondByToken_missingDecision_rejected_stateUnchanged() {
        LocalDateTime deadline = enterPending();
        String token = confirmerService.generateConfirmationToken(confirmer.getId(), deadline);

        DmsException ex = assertThrows(DmsException.class, () -> confirmerService.respondByToken(token, null, now));

        assertEquals(ErrorCode.MISSING_PARAMETER, ex.getErrorCode());
        assertEquals(DmsStatus.PENDING_CONFIRMATION, config.getStatus());
        assertEquals(ExecutorStatus.INACTIVE, primary.getStatus());
    }

    @Test
    @DisplayName("X2 m7: dùng lại token -> CONFIRMER_TOKEN_INVALID; kiểm jti SAU khi khoá DmsConfig")
    void respondByToken_reusedToken_rejected_jtiCheckedAfterLock() {
        LocalDateTime deadline = enterPending();
        String token = confirmerService.generateConfirmationToken(confirmer.getId(), deadline);
        when(dmsConfirmerRepository.existsByLastTokenJti(any())).thenReturn(true);

        DmsException ex = assertThrows(DmsException.class,
                () -> confirmerService.respondByToken(token, DmsConfirmerDecision.UNREACHABLE, now));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals(ErrorCode.CONFIRMER_TOKEN_INVALID, ex.getErrorCode());
        assertEquals(DmsStatus.PENDING_CONFIRMATION, config.getStatus());
        InOrder order = inOrder(dmsConfigRepository, dmsConfirmerRepository);
        order.verify(dmsConfigRepository).findByOwnerIdForUpdate(owner.getId());
        order.verify(dmsConfirmerRepository).existsByLastTokenJti(anyString());
    }

    @Test
    @DisplayName("X2: mỗi confirmer mỗi đợt 1 token; token của đợt khác -> CONFIRMER_TOKEN_INVALID")
    void respondByToken_tokenFromAnotherRound_rejected() {
        LocalDateTime deadline = enterPending();
        String oldRoundToken = confirmerService.generateConfirmationToken(confirmer.getId(), deadline.minusDays(10));

        DmsException ex = assertThrows(DmsException.class,
                () -> confirmerService.respondByToken(oldRoundToken, DmsConfirmerDecision.UNREACHABLE, now));

        assertEquals(ErrorCode.CONFIRMER_TOKEN_INVALID, ex.getErrorCode());
        assertEquals(DmsStatus.PENDING_CONFIRMATION, config.getStatus());
        assertEquals(ExecutorStatus.INACTIVE, primary.getStatus());
    }

    // ---------- hết hạn / không gửi được mail ----------

    @Test
    @DisplayName("X2 Nhánh 3: quá 72 giờ không ai bấm -> TRIGGERED và kích hoạt Executor")
    void processExpiredConfirmation_after72Hours_autoTriggers() {
        config.setStatus(DmsStatus.PENDING_CONFIRMATION);
        config.setNextCheckinDueAt(now.minusMinutes(5));

        processor.processExpiredConfirmation(config.getId(), now);

        assertEquals(DmsStatus.TRIGGERED, config.getStatus());
        assertEquals(ExecutorStatus.ACTIVE, primary.getStatus());
        verify(auditLogService).log(eq(AuditAction.DMS_CONFIRMATION_TIMEOUT), eq(AuditResult.SUCCESS),
                isNull(), isNull(), isNull(), eq(DmsActivationProcessor.ENTITY_DMS_CONFIG),
                eq(config.getId().toString()), any());
    }

    @Test
    @DisplayName("X2 M3: không gửi được mail cho ai -> kích hoạt như luồng cũ, audit DMS_CONFIRMATION_UNDELIVERED")
    void activateBecauseConfirmersUnreached_sameRound_triggersWithAudit() {
        LocalDateTime deadline = enterPending();

        processor.activateBecauseConfirmersUnreached(config.getId(), deadline, now);

        assertEquals(DmsStatus.TRIGGERED, config.getStatus());
        assertEquals(ExecutorStatus.ACTIVE, primary.getStatus());
        verify(auditLogService).log(eq(AuditAction.DMS_CONFIRMATION_UNDELIVERED), eq(AuditResult.SUCCESS),
                isNull(), isNull(), isNull(), eq(DmsActivationProcessor.ENTITY_DMS_CONFIG),
                eq(config.getId().toString()), contains("reason=NO_CONFIRMATION_EMAIL_SENT"));
    }

    @Test
    @DisplayName("X2 M3: Owner đã check-in (đợt khác) trước khi listener chạy -> không kích hoạt")
    void activateBecauseConfirmersUnreached_otherRound_noop() {
        LocalDateTime deadline = enterPending();
        config.setStatus(DmsStatus.ACTIVE);   // Owner vừa check-in

        processor.activateBecauseConfirmersUnreached(config.getId(), deadline, now);

        assertEquals(DmsStatus.ACTIVE, config.getStatus());
        assertEquals(ExecutorStatus.INACTIVE, primary.getStatus());
    }

    @Test
    @DisplayName("X2 C1: prepareConfirmationMails sinh 1 token/confirmer, token dùng được đúng đợt đó")
    void prepareConfirmationMails_oneTokenPerConfirmer_validForThisRound() {
        LocalDateTime deadline = enterPending();

        DmsConfirmerService.ConfirmationMails mails = confirmerService.prepareConfirmationMails(config.getId(), deadline);

        assertNotNull(mails);
        assertEquals(1, mails.confirmers().size());
        assertEquals("owner@example.com", mails.ownerEmail());
        String token = mails.confirmers().get(0).token();
        assertEquals(DmsStatus.ACTIVE,
                confirmerService.respondByToken(token, DmsConfirmerDecision.ALIVE, now).getDmsStatus());
        assertNull(confirmerService.prepareConfirmationMails(config.getId(), deadline.minusHours(1)));
    }

    // ---------- addConfirmer / removeConfirmer ----------

    @Test
    @DisplayName("X2 M2: gói FREE -> PlanLimitReachedException (TRUSTED_CONFIRMERS), không lưu")
    void addConfirmer_freePlan_planLimitReached() {
        doThrow(new PlanLimitReachedException(PlanFeature.TRUSTED_CONFIRMERS, "VIP only"))
                .when(entitlementService).assertCanUseTrustedConfirmers(owner.getId());

        PlanLimitReachedException ex = assertThrows(PlanLimitReachedException.class,
                () -> confirmerService.addConfirmer(owner.getId(), request("c@example.com")));

        assertEquals(ErrorCode.PLAN_LIMIT_REACHED, ex.getErrorCode());
        verify(dmsConfirmerRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("X2 m1: Executor còn hiệu lực của Owner -> CONFLICT, không lưu")
    void addConfirmer_executorOfOwner_conflict() {
        when(executorAssignmentRepository.existsLiveExecutorWithEmail(owner.getId(), "primary@example.com")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> confirmerService.addConfirmer(owner.getId(), request(" Primary@Example.com ")));

        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
        verify(dmsConfirmerRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("X2 m4: tự thêm mình -> BAD_REQUEST (không dùng BENEFICIARY_SELF)")
    void addConfirmer_self_badRequest() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> confirmerService.addConfirmer(owner.getId(), request("OWNER@example.com")));
        assertEquals(ErrorCode.BAD_REQUEST, ex.getErrorCode());
    }

    @Test
    @DisplayName("X2 m4: đủ 2 người -> CONFLICT; trùng email -> CONFLICT (n7); DMS chưa cấu hình -> CONFLICT")
    void addConfirmer_limitDuplicateNotConfigured_useExistingCodes() {
        when(dmsConfirmerRepository.countByDmsConfigId(config.getId())).thenReturn(2L);
        assertEquals(ErrorCode.CONFLICT, assertThrows(BusinessException.class,
                () -> confirmerService.addConfirmer(owner.getId(), request("c@example.com"))).getErrorCode());

        when(dmsConfirmerRepository.countByDmsConfigId(config.getId())).thenReturn(1L);
        when(dmsConfirmerRepository.existsByDmsConfigIdAndEmailIgnoreCase(config.getId(), "c@example.com")).thenReturn(true);
        assertEquals(ErrorCode.CONFLICT, assertThrows(BusinessException.class,
                () -> confirmerService.addConfirmer(owner.getId(), request("c@example.com"))).getErrorCode());

        UUID otherOwner = UUID.randomUUID();
        when(dmsConfigRepository.findByOwnerIdForUpdate(otherOwner)).thenReturn(Optional.empty());
        assertEquals(ErrorCode.CONFLICT, assertThrows(BusinessException.class,
                () -> confirmerService.addConfirmer(otherOwner, request("c@example.com"))).getErrorCode());
    }

    @Test
    @DisplayName("X2: thêm hợp lệ (VIP, không phải Executor) -> lưu email đã chuẩn hoá")
    void addConfirmer_valid_saves() {
        ArgumentCaptor<DmsConfirmer> saved = ArgumentCaptor.forClass(DmsConfirmer.class);
        when(dmsConfirmerRepository.saveAndFlush(saved.capture())).thenAnswer(inv -> inv.getArgument(0));

        confirmerService.addConfirmer(owner.getId(), request("  C@Example.com "));

        assertEquals("c@example.com", saved.getValue().getEmail());
        verify(entitlementService).assertCanUseTrustedConfirmers(owner.getId());
    }

    @Test
    @DisplayName("X2 m4: xoá confirmer không tồn tại -> NOT_FOUND")
    void removeConfirmer_notFound() {
        when(dmsConfirmerRepository.findByIdAndDmsConfigId(any(), eq(config.getId()))).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> confirmerService.removeConfirmer(owner.getId(), UUID.randomUUID()));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("X2: hạn chờ cắt về micro giây (khớp DATETIME(6))")
    void deadline_isTruncatedToMicros() {
        now = now.plusNanos(123_456_789);
        LocalDateTime deadline = enterPending();
        assertEquals(deadline.truncatedTo(ChronoUnit.MICROS), deadline);
    }
}
