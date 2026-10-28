package com.ltld.app.legacyvault.service.dmsservice;

import com.ltld.app.legacyvault.entity.DmsConfig;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.Notification;
import com.ltld.app.legacyvault.entity.Role;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.DmsStatus;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.ExecutorType;
import com.ltld.app.legacyvault.enums.NotificationStatus;
import com.ltld.app.legacyvault.enums.NotificationType;
import com.ltld.app.legacyvault.repository.DmsCheckinRepository;
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import com.ltld.app.legacyvault.repository.NotificationRepository;
import com.ltld.app.legacyvault.repository.RoleRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import com.ltld.app.legacyvault.service.executorservice.ExecutorAssignmentChangedEvent;
import com.ltld.app.legacyvault.service.executorservice.ExecutorRoleManager;
import com.ltld.app.legacyvault.service.notificationservice.ExecutorNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DmsActivationProcessorTest {

    @Mock private DmsConfigRepository dmsConfigRepository;
    @Mock private DmsCheckinRepository dmsCheckinRepository;
    @Mock private ExecutorAssignmentRepository executorAssignmentRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private ExecutorNotificationService executorNotificationService;
    @Mock private UserRepository userRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private DmsActivationProcessor processor;

    private UUID ownerId;
    private UUID primaryUserId;
    private UUID backupUserId;
    private DmsConfig config;
    private ExecutorAssignment primary;
    private ExecutorAssignment backup;
    private Role executorRole;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        // ExecutorRoleManager thật dựng từ các mock trên, để assertion trên roleRepository giữ nguyên.
        processor = new DmsActivationProcessor(dmsConfigRepository, dmsCheckinRepository, executorAssignmentRepository,
                notificationRepository, new ExecutorRoleManager(roleRepository, executorAssignmentRepository),
                auditLogService, executorNotificationService, userRepository, eventPublisher);

        ownerId = UUID.randomUUID();
        primaryUserId = UUID.randomUUID();
        backupUserId = UUID.randomUUID();
        now = LocalDateTime.of(2026, 10, 1, 12, 0);

        User owner = User.builder().id(ownerId).fullName("Nguyễn Văn A").email("owner@example.test").build();
        config = DmsConfig.builder().id(UUID.randomUUID()).owner(owner).status(DmsStatus.ACTIVE)
                .nextCheckinDueAt(now.minusDays(1)).backupActivationWaitDays(14)
                .lastCheckinAt(now.minusDays(38)).checkinIntervalDays(30).gracePeriodDays(7).reminderCount(3)
                // Đã gửi được 1 lần nhắc: các test trigger không rơi vào nhánh hoãn Q27 (B3).
                .remindersSent(1).build();
        primary = assignment(owner, primaryUserId, "primary@example.test", ExecutorType.PRIMARY);
        backup = assignment(owner, backupUserId, "backup@example.test", ExecutorType.BACKUP);

        executorRole = new Role();
        executorRole.setCode("EXECUTOR");
        when(roleRepository.findByCode("EXECUTOR")).thenReturn(Optional.of(executorRole));

        when(dmsConfigRepository.findById(config.getId())).thenReturn(Optional.of(config));
        when(executorAssignmentRepository.findByOwnerIdAndExecutorType(ownerId, ExecutorType.PRIMARY))
                .thenReturn(Optional.of(primary));
        when(executorAssignmentRepository.findByOwnerIdAndExecutorType(ownerId, ExecutorType.BACKUP))
                .thenReturn(Optional.of(backup));
        when(executorAssignmentRepository.findAllByOwnerId(ownerId)).thenReturn(List.of(primary, backup));
    }

    private ExecutorAssignment assignment(User owner, UUID userId, String email, ExecutorType type) {
        return ExecutorAssignment.builder().id(UUID.randomUUID()).owner(owner)
                .executorUser(User.builder().id(userId).email(email).build())
                .executorType(type).status(ExecutorStatus.INACTIVE).build();
    }

    private Notification activationNotification(UUID recipientId, NotificationType type, NotificationStatus status,
                                                LocalDateTime sentAt) {
        Notification n = Notification.builder().type(type).status(status).sentAt(sentAt)
                .content("content").referenceType("DmsConfig").referenceId(config.getId()).build();
        when(notificationRepository
                .findFirstByRecipientIdAndTypeAndReferenceTypeAndReferenceIdOrderByCreatedAtDesc(
                        recipientId, type, "DmsConfig", config.getId()))
                .thenReturn(Optional.of(n));
        return n;
    }

    private void primaryActiveAndNotified(NotificationStatus status, LocalDateTime sentAt) {
        config.setStatus(DmsStatus.TRIGGERED);
        primary.setStatus(ExecutorStatus.ACTIVE);
        activationNotification(primaryUserId, NotificationType.EXECUTOR_ACTIVATED, status, sentAt);
    }

    // Audit của scheduler: actor/email null, vaultId null (DMS gắn với Owner, không với Vault).
    private List<String> auditActions() {
        ArgumentCaptor<AuditAction> captor = ArgumentCaptor.forClass(AuditAction.class);
        verify(auditLogService, org.mockito.Mockito.atLeast(0)).log(captor.capture(), eq(AuditResult.SUCCESS),
                isNull(), isNull(), isNull(), anyString(), anyString(), anyString());
        return captor.getAllValues().stream().map(Enum::name).toList();
    }

    // ---------- trigger ----------

    @Test
    @DisplayName("FR-07: DMS quá hạn -> TRIGGERED, Primary ACTIVE và nhận thông báo SENT")
    void trigger_activatesPrimaryAndNotifies() {
        processor.trigger(config.getId(), now);

        assertThat(config.getStatus()).isEqualTo(DmsStatus.TRIGGERED);
        assertThat(config.getTriggeredAt()).isEqualTo(now);
        assertThat(primary.getStatus()).isEqualTo(ExecutorStatus.ACTIVE);
        assertThat(primary.getActivatedAt()).isEqualTo(now);
        assertThat(backup.getStatus()).isEqualTo(ExecutorStatus.INACTIVE);
        verify(dmsCheckinRepository).save(any());
        verify(executorNotificationService).send(anyString(), anyString());
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.EXECUTOR_ACTIVATED);
        assertThat(captor.getValue().getReferenceType()).isEqualTo("DmsConfig");
        assertThat(captor.getValue().getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(captor.getValue().getSentAt()).isEqualTo(now);
        assertThat(auditActions()).containsExactly("DMS_TRIGGERED", "EXECUTOR_ACTIVATED");
        verify(auditLogService).log(eq(AuditAction.EXECUTOR_ACTIVATED), eq(AuditResult.SUCCESS),
                isNull(), isNull(), isNull(), eq("ExecutorAssignment"), eq(primary.getId().toString()),
                contains("executorType=PRIMARY"));
    }

    @Test
    @DisplayName("B1: flush trạng thái trước khi gửi mail và ghi audit (lỗi @Version nổ ra trước audit)")
    void trigger_flushesBeforeMailAndAudit() {
        processor.trigger(config.getId(), now);

        InOrder order = inOrder(executorAssignmentRepository, executorNotificationService, auditLogService);
        order.verify(executorAssignmentRepository).save(primary);
        order.verify(executorAssignmentRepository).flush();
        order.verify(executorNotificationService).send(anyString(), anyString());
        order.verify(auditLogService).log(eq(AuditAction.EXECUTOR_ACTIVATED), any(), any(), any(), any(),
                any(), any(), any());
    }

    @Test
    @DisplayName("B1: flush lỗi (xung đột @Version) -> không gửi mail, không ghi audit")
    void trigger_flushConflictStopsBeforeSideEffects() {
        doThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(ExecutorAssignment.class, primary.getId()))
                .when(executorAssignmentRepository).flush();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> processor.trigger(config.getId(), now))
                .isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class);

        verify(executorNotificationService, never()).send(anyString(), anyString());
        verifyNoInteractions(auditLogService);
    }

    @Test
    @DisplayName("trigger bỏ qua nếu Owner vừa check-in (không còn quá hạn) hoặc DMS không còn ACTIVE")
    void trigger_skipsWhenNoLongerOverdueOrNotActive() {
        config.setNextCheckinDueAt(now.plusDays(1));
        processor.trigger(config.getId(), now);
        assertThat(config.getStatus()).isEqualTo(DmsStatus.ACTIVE);

        config.setNextCheckinDueAt(now.minusDays(1));
        config.setStatus(DmsStatus.TRIGGERED);
        processor.trigger(config.getId(), now);
        verify(executorNotificationService, never()).send(anyString(), anyString());
    }

    @Test
    @DisplayName("Gửi mail lỗi: DMS vẫn TRIGGERED, thông báo ghi FAILED và không có sentAt")
    void trigger_mailFailureRecordedAsFailedWithoutRollback() {
        doThrow(new IllegalStateException("smtp down")).when(executorNotificationService).send(anyString(), anyString());

        processor.trigger(config.getId(), now);

        assertThat(config.getStatus()).isEqualTo(DmsStatus.TRIGGERED);
        assertThat(primary.getStatus()).isEqualTo(ExecutorStatus.ACTIVE);
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(captor.getValue().getSentAt()).isNull();
        assertThat(captor.getValue().getAttemptCount()).isEqualTo(1);
        assertThat(captor.getValue().getLastAttemptAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("Không có Primary hợp lệ -> kích hoạt thẳng Backup")
    void trigger_noPrimaryActivatesBackupDirectly() {
        when(executorAssignmentRepository.findByOwnerIdAndExecutorType(ownerId, ExecutorType.PRIMARY))
                .thenReturn(Optional.empty());

        processor.trigger(config.getId(), now);

        assertThat(backup.getStatus()).isEqualTo(ExecutorStatus.ACTIVE);
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.EXECUTOR_ACTIVATED);
        assertThat(auditActions()).containsExactly("DMS_TRIGGERED", "EXECUTOR_ACTIVATED");
    }

    @Test
    @DisplayName("Primary đã REVOKED (FR-06) cũng được coi là không hợp lệ")
    void trigger_revokedPrimaryActivatesBackup() {
        primary.setStatus(ExecutorStatus.REVOKED);
        processor.trigger(config.getId(), now);
        assertThat(primary.getStatus()).isEqualTo(ExecutorStatus.REVOKED);
        assertThat(backup.getStatus()).isEqualTo(ExecutorStatus.ACTIVE);
    }

    @Test
    @DisplayName("Không có Executor nào -> DMS TRIGGERED và ghi audit cảnh báo, không lỗi")
    void trigger_noExecutorsWritesWarningAudit() {
        when(executorAssignmentRepository.findByOwnerIdAndExecutorType(ownerId, ExecutorType.PRIMARY))
                .thenReturn(Optional.empty());
        when(executorAssignmentRepository.findByOwnerIdAndExecutorType(ownerId, ExecutorType.BACKUP))
                .thenReturn(Optional.empty());

        processor.trigger(config.getId(), now);

        assertThat(config.getStatus()).isEqualTo(DmsStatus.TRIGGERED);
        verify(executorNotificationService, never()).send(anyString(), anyString());
        assertThat(auditActions()).containsExactly("DMS_TRIGGERED", "DMS_TRIGGERED_NO_EXECUTOR");
    }

    // ---------- B6: báo Admin khi không có Executor ----------

    private void noExecutors() {
        when(executorAssignmentRepository.findByOwnerIdAndExecutorType(ownerId, ExecutorType.PRIMARY))
                .thenReturn(Optional.empty());
        when(executorAssignmentRepository.findByOwnerIdAndExecutorType(ownerId, ExecutorType.BACKUP))
                .thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("B6: không có Executor -> mọi Admin ACTIVE nhận mail kèm Owner và mã DmsConfig")
    void trigger_noExecutors_mailsEveryActiveAdmin() {
        noExecutors();
        when(userRepository.findActiveByRoleCode("ADMIN")).thenReturn(List.of(
                User.builder().id(UUID.randomUUID()).email("admin1@example.test").build(),
                User.builder().id(UUID.randomUUID()).email("admin2@example.test").build()));

        processor.trigger(config.getId(), now);

        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(executorNotificationService).send(eq("admin1@example.test"), content.capture());
        verify(executorNotificationService).send(eq("admin2@example.test"), anyString());
        assertThat(content.getValue()).contains("Nguyễn Văn A (owner@example.test)", config.getId().toString());
        // Không tạo Notification EXECUTOR_ACTIVATED cho Admin.
        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("B6: gửi mail cho một Admin lỗi -> vẫn gửi Admin còn lại, DMS vẫn TRIGGERED")
    void trigger_noExecutors_adminMailFailureDoesNotStopOthers() {
        noExecutors();
        when(userRepository.findActiveByRoleCode("ADMIN")).thenReturn(List.of(
                User.builder().id(UUID.randomUUID()).email("down@example.test").build(),
                User.builder().id(UUID.randomUUID()).email("ok@example.test").build()));
        doThrow(new RuntimeException("smtp down")).when(executorNotificationService)
                .send(eq("down@example.test"), anyString());

        processor.trigger(config.getId(), now);

        assertThat(config.getStatus()).isEqualTo(DmsStatus.TRIGGERED);
        verify(executorNotificationService).send(eq("ok@example.test"), anyString());
    }

    @Test
    @DisplayName("B6: chưa có Admin nào / lỗi tải Admin -> không lỗi, không gửi mail")
    void trigger_noExecutors_noAdminOrLookupFailure_isSilent() {
        noExecutors();
        when(userRepository.findActiveByRoleCode("ADMIN"))
                .thenReturn(List.of())
                .thenThrow(new RuntimeException("db down"));

        processor.trigger(config.getId(), now);
        config.setStatus(DmsStatus.ACTIVE);
        processor.trigger(config.getId(), now);

        verify(executorNotificationService, never()).send(anyString(), anyString());
        verify(userRepository, times(2)).findActiveByRoleCode("ADMIN");
    }

    @Test
    @DisplayName("B6: có Executor -> không báo Admin")
    void trigger_withExecutor_doesNotNotifyAdmins() {
        processor.trigger(config.getId(), now);
        verify(userRepository, never()).findActiveByRoleCode(anyString());
    }

    // ---------- B3: Q27 hoãn kích hoạt khi Owner chưa nhận được lần nhắc nào ----------

    @Test
    @DisplayName("B3: chưa gửi được lần nhắc nào -> hoãn 24h, nhắc bù ngay, không kích hoạt")
    void trigger_noReminderSent_defersTwentyFourHours() {
        config.setRemindersSent(0);

        processor.trigger(config.getId(), now);

        assertThat(config.getStatus()).isEqualTo(DmsStatus.ACTIVE);
        assertThat(config.getTriggerDeferrals()).isEqualTo(1);
        assertThat(config.getNextCheckinDueAt()).isEqualTo(now.plusHours(24));
        assertThat(config.getNextReminderAt()).isEqualTo(now);
        assertThat(primary.getStatus()).isEqualTo(ExecutorStatus.INACTIVE);
        verify(dmsConfigRepository).saveAndFlush(config);
        verify(dmsCheckinRepository, never()).save(any());
        verifyNoInteractions(executorNotificationService);
        assertThat(auditActions()).containsExactly("DMS_TRIGGER_DEFERRED");
        verify(auditLogService).log(eq(AuditAction.DMS_TRIGGER_DEFERRED), eq(AuditResult.SUCCESS),
                isNull(), isNull(), isNull(), eq("DmsConfig"), eq(config.getId().toString()),
                contains("deferral=1"));
    }

    @Test
    @DisplayName("B3: đã hoãn 2 lần -> lần thứ 3 vẫn hoãn")
    void trigger_thirdDeferralStillDefers() {
        config.setRemindersSent(0);
        config.setTriggerDeferrals(2);

        processor.trigger(config.getId(), now);

        assertThat(config.getStatus()).isEqualTo(DmsStatus.ACTIVE);
        assertThat(config.getTriggerDeferrals()).isEqualTo(3);
    }

    @Test
    @DisplayName("B3: đã hoãn đủ 3 lần -> kích hoạt dù chưa gửi được lần nhắc nào")
    void trigger_afterMaxDeferrals_triggers() {
        config.setRemindersSent(0);
        config.setTriggerDeferrals(3);

        processor.trigger(config.getId(), now);

        assertThat(config.getStatus()).isEqualTo(DmsStatus.TRIGGERED);
        assertThat(config.getTriggerDeferrals()).isEqualTo(3);
        assertThat(primary.getStatus()).isEqualTo(ExecutorStatus.ACTIVE);
        assertThat(auditActions()).containsExactly("DMS_TRIGGERED", "EXECUTOR_ACTIVATED");
    }

    @Test
    @DisplayName("B3: đã gửi được lần nhắc -> kích hoạt ngay, không hoãn")
    void trigger_reminderSent_doesNotDefer() {
        processor.trigger(config.getId(), now);

        assertThat(config.getStatus()).isEqualTo(DmsStatus.TRIGGERED);
        assertThat(config.getTriggerDeferrals()).isZero();
        verify(dmsConfigRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("B3: dòng tạo trước FR-03 (thiếu lịch nhắc) -> không hoãn, kích hoạt ngay")
    void trigger_noReminderSchedule_doesNotDefer() {
        config.setRemindersSent(0);
        config.setReminderCount(null);

        processor.trigger(config.getId(), now);

        assertThat(config.getStatus()).isEqualTo(DmsStatus.TRIGGERED);
        assertThat(config.getTriggerDeferrals()).isZero();
    }

    @Test
    @DisplayName("B3: DMS không còn quá hạn -> không hoãn, không ghi audit")
    void trigger_notOverdue_doesNotDefer() {
        config.setRemindersSent(0);
        config.setNextCheckinDueAt(now.plusDays(1));

        processor.trigger(config.getId(), now);

        assertThat(config.getTriggerDeferrals()).isZero();
        verifyNoInteractions(auditLogService);
    }

    // ---------- escalate ----------

    @Test
    @DisplayName("3a/BR-03: đủ 14 ngày (đúng biên) Primary chưa xác nhận -> REVOKED, Backup ACTIVE + thông báo")
    void escalate_revokesPrimaryAndActivatesBackupAtExactlyFourteenDays() {
        primaryActiveAndNotified(NotificationStatus.SENT, now.minusDays(14));

        processor.escalateIfPrimaryUnresponsive(config.getId(), now);

        assertThat(primary.getStatus()).isEqualTo(ExecutorStatus.REVOKED);
        assertThat(primary.getRevokedAt()).isEqualTo(now);
        assertThat(backup.getStatus()).isEqualTo(ExecutorStatus.ACTIVE);
        assertThat(backup.getActivatedAt()).isEqualTo(now);
        verify(executorAssignmentRepository).save(primary);
        verify(executorAssignmentRepository).save(backup);
        InOrder order = inOrder(executorAssignmentRepository, auditLogService);
        order.verify(executorAssignmentRepository).save(backup);
        order.verify(executorAssignmentRepository).flush();
        order.verify(auditLogService).log(eq(AuditAction.EXECUTOR_REVOKE), any(), any(), any(), any(),
                any(), any(), any());
        assertThat(auditActions()).containsExactly("EXECUTOR_REVOKE", "EXECUTOR_ACTIVATED");
        verify(auditLogService).log(eq(AuditAction.EXECUTOR_REVOKE), eq(AuditResult.SUCCESS),
                isNull(), isNull(), isNull(), eq("ExecutorAssignment"), eq(primary.getId().toString()),
                contains("executorType=PRIMARY, reason=BR03_TIMEOUT"));
    }

    @Test
    @DisplayName("B3: leo thang -> Primary bị gỡ role EXECUTOR và nhận mail REVOKED")
    void escalate_revokesPrimaryRoleAndMailsPrimary() {
        primaryActiveAndNotified(NotificationStatus.SENT, now.minusDays(14));
        primary.getExecutorUser().getRoles().add(executorRole);

        processor.escalateIfPrimaryUnresponsive(config.getId(), now);

        assertThat(primary.getExecutorUser().getRoles()).doesNotContain(executorRole);
        ArgumentCaptor<ExecutorAssignmentChangedEvent> captor = ArgumentCaptor.forClass(ExecutorAssignmentChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().mails()).singleElement().satisfies(mail -> {
            assertThat(mail.type()).isEqualTo(ExecutorAssignmentChangedEvent.MailType.REVOKED);
            assertThat(mail.recipientEmail()).isEqualTo("primary@example.test");
            assertThat(mail.executorType()).isEqualTo(ExecutorType.PRIMARY);
        });
    }

    @Test
    @DisplayName("B3: Primary còn làm Executor cho Owner khác -> giữ role EXECUTOR")
    void escalate_keepsRoleWhenPrimaryStillAssignedElsewhere() {
        primaryActiveAndNotified(NotificationStatus.SENT, now.minusDays(14));
        primary.getExecutorUser().getRoles().add(executorRole);
        when(executorAssignmentRepository.existsByExecutorUserIdAndStatusNotAndIdNot(
                primaryUserId, ExecutorStatus.REVOKED, primary.getId())).thenReturn(true);

        processor.escalateIfPrimaryUnresponsive(config.getId(), now);

        assertThat(primary.getExecutorUser().getRoles()).contains(executorRole);
    }

    @Test
    @DisplayName("B3: chưa leo thang -> không phát mail REVOKED")
    void escalate_noEscalationPublishesNothing() {
        primaryActiveAndNotified(NotificationStatus.SENT, now.minusDays(13));
        processor.escalateIfPrimaryUnresponsive(config.getId(), now);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("Chưa đủ 14 ngày -> không leo thang")
    void escalate_doesNothingBeforeFourteenDays() {
        primaryActiveAndNotified(NotificationStatus.SENT, now.minusDays(13));
        processor.escalateIfPrimaryUnresponsive(config.getId(), now);
        assertThat(primary.getStatus()).isEqualTo(ExecutorStatus.ACTIVE);
        assertThat(backup.getStatus()).isEqualTo(ExecutorStatus.INACTIVE);
    }

    @Test
    @DisplayName("Primary đã xác nhận (acknowledgedAt) -> không leo thang dù quá 14 ngày")
    void escalate_doesNothingWhenPrimaryAcknowledged() {
        primaryActiveAndNotified(NotificationStatus.SENT, now.minusDays(30));
        primary.setAcknowledgedAt(now.minusDays(20));
        processor.escalateIfPrimaryUnresponsive(config.getId(), now);
        assertThat(primary.getStatus()).isEqualTo(ExecutorStatus.ACTIVE);
        assertThat(backup.getStatus()).isEqualTo(ExecutorStatus.INACTIVE);
    }

    @Test
    @DisplayName("Số ngày chờ lấy từ cấu hình DMS")
    void escalate_usesConfiguredWaitDays() {
        config.setBackupActivationWaitDays(3);
        primaryActiveAndNotified(NotificationStatus.SENT, now.minusDays(3));
        processor.escalateIfPrimaryUnresponsive(config.getId(), now);
        assertThat(backup.getStatus()).isEqualTo(ExecutorStatus.ACTIVE);
    }

    @Test
    @DisplayName("Thông báo FAILED được gửi lại; đồng hồ 14 ngày bắt đầu từ lúc gửi thành công nên chưa leo thang")
    void escalate_retriesFailedNotificationAndRestartsClock() {
        config.setStatus(DmsStatus.TRIGGERED);
        primary.setStatus(ExecutorStatus.ACTIVE);
        Notification failed = activationNotification(primaryUserId, NotificationType.EXECUTOR_ACTIVATED,
                NotificationStatus.FAILED, null);

        processor.escalateIfPrimaryUnresponsive(config.getId(), now);

        assertThat(failed.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(failed.getSentAt()).isEqualTo(now);
        assertThat(failed.getAttemptCount()).isEqualTo(1);
        assertThat(failed.getLastAttemptAt()).isEqualTo(now);
        verify(notificationRepository).save(failed);
        assertThat(primary.getStatus()).isEqualTo(ExecutorStatus.ACTIVE);
        assertThat(backup.getStatus()).isEqualTo(ExecutorStatus.INACTIVE);
    }

    @Test
    @DisplayName("Gửi lại vẫn lỗi -> giữ FAILED, không leo thang")
    void escalate_keepsFailedWhenRetryFails() {
        config.setStatus(DmsStatus.TRIGGERED);
        primary.setStatus(ExecutorStatus.ACTIVE);
        Notification failed = activationNotification(primaryUserId, NotificationType.EXECUTOR_ACTIVATED,
                NotificationStatus.FAILED, null);
        doThrow(new IllegalStateException("smtp down")).when(executorNotificationService).send(anyString(), anyString());

        processor.escalateIfPrimaryUnresponsive(config.getId(), now);

        assertThat(failed.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(failed.getAttemptCount()).isEqualTo(1);
        assertThat(failed.getLastAttemptAt()).isEqualTo(now);
        verify(notificationRepository).save(failed);
        assertThat(backup.getStatus()).isEqualTo(ExecutorStatus.INACTIVE);
    }

    // ---------- B2: đồng hồ BR-03 khi mail lỗi + giới hạn gửi lại ----------

    private Notification primaryNeverDelivered(LocalDateTime activatedAt) {
        config.setStatus(DmsStatus.TRIGGERED);
        primary.setStatus(ExecutorStatus.ACTIVE);
        primary.setActivatedAt(activatedAt);
        doThrow(new IllegalStateException("smtp down")).when(executorNotificationService).send(anyString(), anyString());
        return activationNotification(primaryUserId, NotificationType.EXECUTOR_ACTIVATED, NotificationStatus.FAILED, null);
    }

    @Test
    @DisplayName("B2/T2: mail Primary lỗi suốt 14 ngày kể từ kích hoạt -> vẫn leo thang sang Backup")
    void escalate_neverDelivered_stillEscalatesAfterWaitFromActivation() {
        primaryNeverDelivered(now.minusDays(14));

        processor.escalateIfPrimaryUnresponsive(config.getId(), now);

        assertThat(primary.getStatus()).isEqualTo(ExecutorStatus.REVOKED);
        assertThat(backup.getStatus()).isEqualTo(ExecutorStatus.ACTIVE);
    }

    @Test
    @DisplayName("B2: mail Primary lỗi, mới 13 ngày kể từ kích hoạt -> chưa leo thang")
    void escalate_neverDelivered_doesNothingBeforeWaitFromActivation() {
        primaryNeverDelivered(now.minusDays(13));

        processor.escalateIfPrimaryUnresponsive(config.getId(), now);

        assertThat(primary.getStatus()).isEqualTo(ExecutorStatus.ACTIVE);
        assertThat(backup.getStatus()).isEqualTo(ExecutorStatus.INACTIVE);
    }

    @Test
    @DisplayName("B2/T3: đã thử 3 lần (chờ 4 giờ), lần cuối mới 3 giờ 59 phút trước -> chưa gửi lại")
    void escalate_doesNotRetryWithinRetryInterval() {
        Notification failed = primaryNeverDelivered(now.minusDays(1));
        failed.setAttemptCount(3);
        failed.setLastAttemptAt(now.minusHours(4).plusMinutes(1));

        processor.escalateIfPrimaryUnresponsive(config.getId(), now);

        verify(executorNotificationService, never()).send(anyString(), anyString());
        verify(notificationRepository, never()).save(any());
        assertThat(failed.getAttemptCount()).isEqualTo(3);
        assertThat(failed.getLastAttemptAt()).isEqualTo(now.minusHours(4).plusMinutes(1));
    }

    @Test
    @DisplayName("B2/T3: đã thử đủ MAX_ATTEMPTS lần -> không gửi lại nữa")
    void escalate_doesNotRetryAfterMaxAttempts() {
        Notification failed = primaryNeverDelivered(now.minusDays(1));
        failed.setAttemptCount(DmsActivationProcessor.MAX_ATTEMPTS);
        failed.setLastAttemptAt(now.minusDays(1));

        processor.escalateIfPrimaryUnresponsive(config.getId(), now);

        verify(executorNotificationService, never()).send(anyString(), anyString());
        assertThat(failed.getAttemptCount()).isEqualTo(DmsActivationProcessor.MAX_ATTEMPTS);
    }

    @Test
    @DisplayName("B2: chưa đủ MAX_ATTEMPTS và đã chờ đúng 24 giờ kể từ lần thử trước -> gửi lại, tăng attemptCount")
    void escalate_retriesAfterRetryInterval() {
        Notification failed = primaryNeverDelivered(now.minusDays(2));
        failed.setAttemptCount(DmsActivationProcessor.MAX_ATTEMPTS - 1);
        failed.setLastAttemptAt(now.minusHours(24));

        processor.escalateIfPrimaryUnresponsive(config.getId(), now);

        verify(executorNotificationService).send(anyString(), anyString());
        assertThat(failed.getAttemptCount()).isEqualTo(DmsActivationProcessor.MAX_ATTEMPTS);
        assertThat(failed.getLastAttemptAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("Không có Backup INACTIVE -> Primary giữ nguyên, không lỗi")
    void escalate_noBackupLeavesPrimaryUntouched() {
        primaryActiveAndNotified(NotificationStatus.SENT, now.minusDays(20));
        when(executorAssignmentRepository.findByOwnerIdAndExecutorType(ownerId, ExecutorType.BACKUP))
                .thenReturn(Optional.empty());
        processor.escalateIfPrimaryUnresponsive(config.getId(), now);
        assertThat(primary.getStatus()).isEqualTo(ExecutorStatus.ACTIVE);
    }

    @Test
    @DisplayName("DMS không ở trạng thái TRIGGERED -> bỏ qua")
    void escalate_ignoresNonTriggeredDms() {
        primary.setStatus(ExecutorStatus.ACTIVE);
        processor.escalateIfPrimaryUnresponsive(config.getId(), now); // config đang ACTIVE
        assertThat(backup.getStatus()).isEqualTo(ExecutorStatus.INACTIVE);
        verify(executorNotificationService, never()).send(anyString(), anyString());
    }

    @Test
    @DisplayName("T8: Backup đã xác nhận sau leo thang, lượt quét tiếp theo không đổi gì và không ghi audit mới")
    void scanAfterEscalationAndBackupAcknowledged_isNoOp() {
        config.setStatus(DmsStatus.TRIGGERED);
        primary.setStatus(ExecutorStatus.REVOKED);
        primary.setRevokedAt(now.minusDays(1));
        backup.setStatus(ExecutorStatus.ACTIVE);
        backup.setActivatedAt(now.minusDays(1));
        LocalDateTime acknowledgedAt = now.minusHours(2);
        backup.setAcknowledgedAt(acknowledgedAt);
        activationNotification(backupUserId, NotificationType.EXECUTOR_ACTIVATED, NotificationStatus.READ,
                now.minusDays(1));

        processor.escalateIfPrimaryUnresponsive(config.getId(), now);
        processor.trigger(config.getId(), now);

        assertThat(primary.getStatus()).isEqualTo(ExecutorStatus.REVOKED);
        assertThat(backup.getStatus()).isEqualTo(ExecutorStatus.ACTIVE);
        assertThat(backup.getAcknowledgedAt()).isEqualTo(acknowledgedAt);
        verify(executorAssignmentRepository, never()).save(any());
        verify(dmsConfigRepository, never()).save(any());
        verify(notificationRepository, never()).save(any());
        verify(executorNotificationService, never()).send(anyString(), anyString());
        verifyNoInteractions(auditLogService);
    }

    // ---------- C2/T7: role EXECUTOR ----------

    @Test
    @DisplayName("C2/T7: trigger -> user của Primary được kích hoạt có role EXECUTOR, Backup thì chưa")
    void trigger_grantsExecutorRoleToActivatedUser() {
        processor.trigger(config.getId(), now);

        assertThat(primary.getExecutorUser().getRoles()).containsExactly(executorRole);
        assertThat(backup.getExecutorUser().getRoles()).isEmpty();
    }

    @Test
    @DisplayName("C2/T7: kích hoạt hai lần -> role EXECUTOR không bị trùng, chỉ tra RoleRepository một lần")
    void trigger_executorRoleIsIdempotent() {
        processor.trigger(config.getId(), now);
        // Lượt kích hoạt thứ hai trên cùng user (ví dụ user là Executor của nhiều Owner).
        config.setStatus(DmsStatus.ACTIVE);
        primary.setStatus(ExecutorStatus.INACTIVE);
        processor.trigger(config.getId(), now);

        assertThat(primary.getExecutorUser().getRoles()).hasSize(1);
        verify(roleRepository, times(1)).findByCode("EXECUTOR");
    }

    @Test
    @DisplayName("C2/T7: leo thang sang Backup -> user của Backup có role EXECUTOR")
    void escalate_grantsExecutorRoleToBackup() {
        primaryActiveAndNotified(NotificationStatus.SENT, now.minusDays(14));

        processor.escalateIfPrimaryUnresponsive(config.getId(), now);

        assertThat(backup.getStatus()).isEqualTo(ExecutorStatus.ACTIVE);
        assertThat(backup.getExecutorUser().getRoles()).containsExactly(executorRole);
    }

    // ---------- C3/T8: lịch gửi lại ----------

    @ParameterizedTest(name = "attemptCount={0} -> chờ {1} giờ")
    @CsvSource({"0, 1", "1, 1", "2, 2", "3, 4", "4, 8", "5, 16", "6, 24", "7, 24", "19, 24"})
    @DisplayName("C3/T8: retryDelay giãn 1h, 2h, 4h, 8h, 16h rồi giữ 24h")
    void retryDelay_followsBackoffTable(int attemptCount, long expectedHours) {
        assertThat(DmsActivationProcessor.retryDelay(attemptCount)).isEqualTo(Duration.ofHours(expectedHours));
    }

    @Test
    @DisplayName("C3/T8: MAX_ATTEMPTS = 20 lần gửi trải dài đủ 14 ngày chờ của BR-03")
    void retrySchedule_coversFourteenDayWait() {
        assertThat(DmsActivationProcessor.MAX_ATTEMPTS).isEqualTo(20);
        Duration span = Duration.ZERO;
        // Cộng khoảng chờ sau mỗi lần thử 1..19, tức từ lần gửi đầu đến lần thử thứ 20.
        for (int attempt = 1; attempt < DmsActivationProcessor.MAX_ATTEMPTS; attempt++) {
            span = span.plus(DmsActivationProcessor.retryDelay(attempt));
        }
        assertThat(span).isGreaterThanOrEqualTo(Duration.ofDays(14));
    }

    // ---------- C4/T9: nội dung mail kích hoạt ----------

    @Test
    @DisplayName("C4/T9: nội dung thông báo nêu tên + email Owner, loại Executor và mã assignment")
    void trigger_notificationContentNamesOwnerTypeAndAssignment() {
        processor.trigger(config.getId(), now);

        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(executorNotificationService).send(eq("primary@example.test"), sent.capture());
        assertThat(sent.getValue())
                .contains("Nguyễn Văn A (owner@example.test)")
                .contains("Primary executor")
                .contains(primary.getId().toString());
        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());
        assertThat(saved.getValue().getContent()).isEqualTo(sent.getValue());
    }

    @Test
    @DisplayName("C4/T9: Owner không có tên -> chỉ in email, không in chữ null; Backup ghi đúng loại")
    void activationMessage_withoutOwnerName_doesNotPrintNull() {
        User owner = User.builder().id(ownerId).email("owner@example.test").build();

        String content = DmsActivationProcessor.activationMessage(owner, backup);

        assertThat(content)
                .contains("The Dead Man's Switch of owner@example.test has been triggered")
                .contains("Backup executor")
                .contains(backup.getId().toString())
                .doesNotContain("null");
    }
}
