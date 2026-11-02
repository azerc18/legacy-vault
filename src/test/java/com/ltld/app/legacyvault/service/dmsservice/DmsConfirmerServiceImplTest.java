package com.ltld.app.legacyvault.service.dmsservice;

import com.ltld.app.legacyvault.dto.dmsdto.DmsConfirmationDecisionResponse;
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
import com.ltld.app.legacyvault.exception.DmsException;
import com.ltld.app.legacyvault.exception.ErrorCode;
import com.ltld.app.legacyvault.repository.DmsCheckinRepository;
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
import com.ltld.app.legacyvault.repository.DmsConfirmerRepository;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import com.ltld.app.legacyvault.repository.NotificationRepository;
import com.ltld.app.legacyvault.repository.RoleRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.security.JwtConfig;
import com.ltld.app.legacyvault.security.JwtProperties;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.executorservice.ExecutorRoleManager;
import com.ltld.app.legacyvault.service.notificationservice.ExecutorNotificationService;
import com.ltld.app.legacyvault.utility.EmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
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

    private DmsActivationProcessor processor;
    private DmsConfirmerServiceImpl confirmerService;

    private User owner;
    private DmsConfig config;
    private DmsConfirmer confirmer;
    private ExecutorAssignment primary;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret("0123456789abcdef0123456789abcdef0123456789abcdef");
        JwtConfig jwtConfig = new JwtConfig(props);
        JwtEncoder jwtEncoder = jwtConfig.jwtEncoder();

        processor = new DmsActivationProcessor(
                dmsConfigRepository,
                dmsCheckinRepository,
                executorAssignmentRepository,
                notificationRepository,
                new ExecutorRoleManager(roleRepository, executorAssignmentRepository),
                auditLogService,
                executorNotificationService,
                userRepository,
                eventPublisher
        );

        confirmerService = new DmsConfirmerServiceImpl(
                dmsConfigRepository,
                dmsConfirmerRepository,
                processor,
                jwtEncoder,
                props,
                emailSender,
                auditLogService,
                "http://localhost:8084",
                "http://localhost:5173"
        );
        processor.setDmsConfirmerService(confirmerService);

        now = LocalDateTime.of(2026, 10, 31, 12, 0);
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
                .lastCheckinAt(now.minusDays(38))
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
    }

    @Test
    @DisplayName("X2: DMS quá hạn khi có Trusted Confirmer -> chuyển sang PENDING_CONFIRMATION (72h), gửi email xác nhận chéo, chưa kích hoạt Executor")
    void trigger_withConfirmer_transitionsToPendingConfirmation() {
        when(dmsConfirmerRepository.findAllByDmsConfigIdOrderByCreatedAtAsc(config.getId()))
                .thenReturn(List.of(confirmer));

        processor.trigger(config.getId(), now);

        assertEquals(DmsStatus.PENDING_CONFIRMATION, config.getStatus());
        assertEquals(now.plusHours(72), config.getNextCheckinDueAt());
        assertEquals(ExecutorStatus.INACTIVE, primary.getStatus());

        verify(emailSender).sendDmsCrossConfirmationRequest(
                eq("confirmer@example.com"),
                eq("Trần Thị B"),
                eq("Nguyễn Văn A"),
                eq("owner@example.com"),
                eq(now.plusHours(72)),
                contains("decision=ALIVE"),
                contains("decision=UNREACHABLE")
        );
        verify(auditLogService).log(
                eq(AuditAction.DMS_CONFIRMATION_REQUESTED),
                eq(AuditResult.SUCCESS),
                isNull(),
                isNull(),
                isNull(),
                eq(DmsActivationProcessor.ENTITY_DMS_CONFIG),
                eq(config.getId().toString()),
                contains("confirmers=1")
        );
    }

    @Test
    @DisplayName("X2 Nhánh 1: Confirmer bấm 'Still safe' (ALIVE) -> hoãn 7 ngày, DMS về ACTIVE, báo cho Owner")
    void respondByToken_alive_postpones7DaysAndKeepsActive() {
        config.setStatus(DmsStatus.PENDING_CONFIRMATION);
        when(dmsConfirmerRepository.findById(confirmer.getId())).thenReturn(Optional.of(confirmer));
        when(dmsConfirmerRepository.existsByLastTokenJti(any())).thenReturn(false);

        String token = confirmerService.generateConfirmationToken(confirmer.getId(), DmsConfirmerDecision.ALIVE);

        DmsConfirmationDecisionResponse res = confirmerService.respondByToken(token, DmsConfirmerDecision.ALIVE, now);

        assertEquals(DmsStatus.ACTIVE, res.getDmsStatus());
        assertEquals(DmsConfirmerDecision.ALIVE, res.getDecision());
        assertEquals(now.plusDays(7), config.getNextCheckinDueAt());
        assertEquals(DmsConfirmerStatus.CONFIRMED_ALIVE, confirmer.getStatus());
        assertEquals(ExecutorStatus.INACTIVE, primary.getStatus());

        verify(emailSender).sendOwnerConfirmedSafeNotice("owner@example.com", "Trần Thị B", now.plusDays(7));
        verify(auditLogService).log(
                eq(AuditAction.DMS_CONFIRMED_ALIVE),
                eq(AuditResult.SUCCESS),
                isNull(),
                isNull(),
                isNull(),
                eq(DmsActivationProcessor.ENTITY_DMS_CONFIG),
                eq(config.getId().toString()),
                contains("postponedUntil=")
        );
    }

    @Test
    @DisplayName("X2 Nhánh 2: Confirmer bấm 'Unreachable' (UNREACHABLE) -> lập tức chuyển TRIGGERED và kích hoạt Primary Executor")
    void respondByToken_unreachable_triggersImmediatelyAndActivatesExecutor() {
        config.setStatus(DmsStatus.PENDING_CONFIRMATION);
        when(dmsConfirmerRepository.findById(confirmer.getId())).thenReturn(Optional.of(confirmer));
        when(dmsConfirmerRepository.existsByLastTokenJti(any())).thenReturn(false);

        String token = confirmerService.generateConfirmationToken(confirmer.getId(), DmsConfirmerDecision.UNREACHABLE);

        DmsConfirmationDecisionResponse res = confirmerService.respondByToken(token, DmsConfirmerDecision.UNREACHABLE, now);

        assertEquals(DmsStatus.TRIGGERED, res.getDmsStatus());
        assertEquals(DmsStatus.TRIGGERED, config.getStatus());
        assertEquals(DmsConfirmerStatus.CONFIRMED_UNREACHABLE, confirmer.getStatus());
        assertEquals(ExecutorStatus.ACTIVE, primary.getStatus());

        verify(auditLogService).log(
                eq(AuditAction.DMS_CONFIRMED_UNREACHABLE),
                eq(AuditResult.SUCCESS),
                isNull(),
                isNull(),
                isNull(),
                eq(DmsActivationProcessor.ENTITY_DMS_CONFIG),
                eq(config.getId().toString()),
                contains("confirmerId=")
        );
        verify(auditLogService).log(
                eq(AuditAction.DMS_TRIGGERED),
                eq(AuditResult.SUCCESS),
                isNull(),
                isNull(),
                isNull(),
                eq(DmsActivationProcessor.ENTITY_DMS_CONFIG),
                eq(config.getId().toString()),
                any()
        );
    }

    @Test
    @DisplayName("X2 Nhánh 3: Quá 72 giờ không ai bấm -> tự động chuyển sang TRIGGERED và kích hoạt Executor")
    void processExpiredConfirmation_after72Hours_autoTriggersAndActivatesExecutor() {
        config.setStatus(DmsStatus.PENDING_CONFIRMATION);
        config.setNextCheckinDueAt(now.minusMinutes(5)); // đã quá hạn 72h

        processor.processExpiredConfirmation(config.getId(), now);

        assertEquals(DmsStatus.TRIGGERED, config.getStatus());
        assertEquals(now, config.getTriggeredAt());
        assertEquals(ExecutorStatus.ACTIVE, primary.getStatus());

        verify(auditLogService).log(
                eq(AuditAction.DMS_CONFIRMATION_TIMEOUT),
                eq(AuditResult.SUCCESS),
                isNull(),
                isNull(),
                isNull(),
                eq(DmsActivationProcessor.ENTITY_DMS_CONFIG),
                eq(config.getId().toString()),
                any()
        );
    }

    @Test
    @DisplayName("X2: Dùng lại token xác nhận lần 2 -> trả 400 CONFIRMER_TOKEN_INVALID")
    void respondByToken_reusedToken_throwsConfirmerTokenInvalid() {
        String token = confirmerService.generateConfirmationToken(confirmer.getId(), DmsConfirmerDecision.ALIVE);
        when(dmsConfirmerRepository.existsByLastTokenJti(any())).thenReturn(true);

        DmsException ex = assertThrows(DmsException.class,
                () -> confirmerService.respondByToken(token, DmsConfirmerDecision.ALIVE, now));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals(ErrorCode.CONFIRMER_TOKEN_INVALID, ex.getErrorCode());
    }
}

