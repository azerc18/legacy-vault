package com.ltld.app.legacyvault.service.dmsservice;

import com.ltld.app.legacyvault.entity.AuditLog;
import com.ltld.app.legacyvault.entity.DmsConfig;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.Notification;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.DmsStatus;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.ExecutorType;
import com.ltld.app.legacyvault.enums.NotificationStatus;
import com.ltld.app.legacyvault.enums.NotificationType;
import com.ltld.app.legacyvault.repository.AuditLogRepository;
import com.ltld.app.legacyvault.repository.DmsCheckinRepository;
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import com.ltld.app.legacyvault.repository.NotificationRepository;
import com.ltld.app.legacyvault.service.notificationservice.ExecutorNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DmsActivationProcessorTest {

    @Mock private DmsConfigRepository dmsConfigRepository;
    @Mock private DmsCheckinRepository dmsCheckinRepository;
    @Mock private ExecutorAssignmentRepository executorAssignmentRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private ExecutorNotificationService executorNotificationService;

    @InjectMocks
    private DmsActivationProcessor processor;

    private UUID ownerId;
    private UUID primaryUserId;
    private UUID backupUserId;
    private DmsConfig config;
    private ExecutorAssignment primary;
    private ExecutorAssignment backup;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        primaryUserId = UUID.randomUUID();
        backupUserId = UUID.randomUUID();
        now = LocalDateTime.of(2026, 10, 1, 12, 0);

        User owner = User.builder().id(ownerId).build();
        config = DmsConfig.builder().id(UUID.randomUUID()).owner(owner).status(DmsStatus.ACTIVE)
                .nextCheckinDueAt(now.minusDays(1)).backupActivationWaitDays(14).build();
        primary = assignment(owner, primaryUserId, "primary@example.test", ExecutorType.PRIMARY);
        backup = assignment(owner, backupUserId, "backup@example.test", ExecutorType.BACKUP);

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
                .content("content").referenceType("dms_config").referenceId(config.getId()).build();
        when(notificationRepository
                .findFirstByRecipientIdAndTypeAndReferenceTypeAndReferenceIdOrderByCreatedAtDesc(
                        recipientId, type, "dms_config", config.getId()))
                .thenReturn(Optional.of(n));
        return n;
    }

    private void primaryActiveAndNotified(NotificationStatus status, LocalDateTime sentAt) {
        config.setStatus(DmsStatus.TRIGGERED);
        primary.setStatus(ExecutorStatus.ACTIVE);
        activationNotification(primaryUserId, NotificationType.DMS_TRIGGERED, status, sentAt);
    }

    private List<String> auditActions() {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, org.mockito.Mockito.atLeast(0)).save(captor.capture());
        return captor.getAllValues().stream().map(a -> a.getAction() == null ? null : a.getAction().name()).toList();
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
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.DMS_TRIGGERED);
        assertThat(captor.getValue().getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(captor.getValue().getSentAt()).isEqualTo(now);
        assertThat(auditActions()).containsExactly("DMS_TRIGGERED", "PRIMARY_EXECUTOR_ACTIVATED");
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
        assertThat(auditActions()).contains("BACKUP_EXECUTOR_ACTIVATED");
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
        assertThat(auditActions()).containsExactly("PRIMARY_EXECUTOR_REVOKED", "BACKUP_EXECUTOR_ACTIVATED");
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
        Notification failed = activationNotification(primaryUserId, NotificationType.DMS_TRIGGERED,
                NotificationStatus.FAILED, null);

        processor.escalateIfPrimaryUnresponsive(config.getId(), now);

        assertThat(failed.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(failed.getSentAt()).isEqualTo(now);
        verify(notificationRepository).save(failed);
        assertThat(primary.getStatus()).isEqualTo(ExecutorStatus.ACTIVE);
        assertThat(backup.getStatus()).isEqualTo(ExecutorStatus.INACTIVE);
    }

    @Test
    @DisplayName("Gửi lại vẫn lỗi -> giữ FAILED, không leo thang")
    void escalate_keepsFailedWhenRetryFails() {
        config.setStatus(DmsStatus.TRIGGERED);
        primary.setStatus(ExecutorStatus.ACTIVE);
        Notification failed = activationNotification(primaryUserId, NotificationType.DMS_TRIGGERED,
                NotificationStatus.FAILED, null);
        doThrow(new IllegalStateException("smtp down")).when(executorNotificationService).send(anyString(), anyString());

        processor.escalateIfPrimaryUnresponsive(config.getId(), now);

        assertThat(failed.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(backup.getStatus()).isEqualTo(ExecutorStatus.INACTIVE);
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
}
