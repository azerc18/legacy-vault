package com.ltld.app.legacyvault.service.dmsservice;

import com.ltld.app.legacyvault.dto.executordto.ExecutorActivationResponse;
import com.ltld.app.legacyvault.entity.DmsConfig;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.Notification;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.DmsStatus;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.ExecutorType;
import com.ltld.app.legacyvault.enums.NotificationStatus;
import com.ltld.app.legacyvault.enums.NotificationType;
import com.ltld.app.legacyvault.exception.DmsNotTriggeredException;
import com.ltld.app.legacyvault.exception.ExecutorAssignmentNotFoundException;
import com.ltld.app.legacyvault.exception.ExecutorNotActiveException;
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import com.ltld.app.legacyvault.repository.NotificationRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DmsAutomationServiceImplTest {

    @Mock private DmsConfigRepository dmsConfigRepository;
    @Mock private ExecutorAssignmentRepository executorAssignmentRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private DmsActivationProcessor processor;

    @InjectMocks
    private DmsAutomationServiceImpl service;

    private UUID ownerId;
    private UUID primaryUserId;
    private DmsConfig config;
    private ExecutorAssignment primary;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        primaryUserId = UUID.randomUUID();
        now = LocalDateTime.of(2026, 10, 1, 12, 0);
        User owner = User.builder().id(ownerId).build();
        config = DmsConfig.builder().id(UUID.randomUUID()).owner(owner).status(DmsStatus.TRIGGERED).build();
        primary = ExecutorAssignment.builder().id(UUID.randomUUID()).owner(owner)
                .executorUser(User.builder().id(primaryUserId).email("primary@example.test").build())
                .executorType(ExecutorType.PRIMARY).status(ExecutorStatus.ACTIVE).activatedAt(now).build();

        when(executorAssignmentRepository.findById(primary.getId())).thenReturn(Optional.of(primary));
        when(dmsConfigRepository.findByOwnerId(ownerId)).thenReturn(Optional.of(config));
    }

    // ---------- vòng quét ----------

    @Test
    @DisplayName("Một Owner lỗi không chặn các Owner còn lại trong lượt quét")
    void processOverdueDms_oneFailureDoesNotStopOthers() {
        DmsConfig other = DmsConfig.builder().id(UUID.randomUUID()).build();
        when(dmsConfigRepository.findAllByStatusAndNextCheckinDueAtBefore(DmsStatus.ACTIVE, now))
                .thenReturn(List.of(config, other));
        doThrow(new IllegalStateException("db error")).when(processor).trigger(config.getId(), now);

        service.processOverdueDms(now);

        verify(processor).trigger(config.getId(), now);
        verify(processor).trigger(other.getId(), now);
    }

    @Test
    @DisplayName("Leo thang chạy cho mọi DMS TRIGGERED, lỗi một cái không chặn cái khác")
    void escalateUnresponsivePrimaries_runsForEveryTriggeredConfig() {
        DmsConfig other = DmsConfig.builder().id(UUID.randomUUID()).build();
        when(dmsConfigRepository.findAllByStatus(DmsStatus.TRIGGERED)).thenReturn(List.of(config, other));
        doThrow(new IllegalStateException("boom")).when(processor).escalateIfPrimaryUnresponsive(config.getId(), now);

        service.escalateUnresponsivePrimaries(now);

        verify(processor).escalateIfPrimaryUnresponsive(other.getId(), now);
    }

    // ---------- acknowledge ----------

    @Test
    @DisplayName("Primary xác nhận: set acknowledgedAt, thông báo READ, ghi audit với actor = Executor")
    void acknowledge_happyPath() {
        Notification activation = Notification.builder().type(NotificationType.EXECUTOR_ACTIVATED)
                .status(NotificationStatus.SENT).build();
        when(notificationRepository
                .findFirstByRecipientIdAndTypeAndReferenceTypeAndReferenceIdOrderByCreatedAtDesc(
                        primaryUserId, NotificationType.EXECUTOR_ACTIVATED, "DmsConfig", config.getId()))
                .thenReturn(Optional.of(activation));

        ExecutorActivationResponse response = service.acknowledgeActivation(primary.getId(), "PRIMARY@example.test");

        assertThat(primary.getAcknowledgedAt()).isNotNull();
        assertThat(response.getAcknowledgedAt()).isEqualTo(primary.getAcknowledgedAt());
        assertThat(response.getStatus()).isEqualTo(ExecutorStatus.ACTIVE);
        assertThat(activation.getStatus()).isEqualTo(NotificationStatus.READ);
        verify(executorAssignmentRepository).save(primary);
        // actor = Executor; vaultId null vì DMS gắn với Owner, không với Vault.
        verify(auditLogService).log(eq(AuditAction.EXECUTOR_ACKNOWLEDGED), eq(AuditResult.SUCCESS),
                eq(primaryUserId), eq("primary@example.test"), isNull(),
                eq("ExecutorAssignment"), eq(primary.getId().toString()), contains("ownerId=" + ownerId));
    }

    @Test
    @DisplayName("Xác nhận lần 2 là idempotent: không đổi acknowledgedAt, không ghi audit thêm")
    void acknowledge_isIdempotent() {
        LocalDateTime first = now.minusHours(2);
        primary.setAcknowledgedAt(first);

        ExecutorActivationResponse response = service.acknowledgeActivation(primary.getId(), "primary@example.test");

        assertThat(response.getAcknowledgedAt()).isEqualTo(first);
        verifyNoInteractions(auditLogService);
    }

    @Test
    @DisplayName("Chấp nhận định danh là user id thay vì email")
    void acknowledge_acceptsUserIdAsLogin() {
        ExecutorActivationResponse response = service.acknowledgeActivation(primary.getId(), primaryUserId.toString());
        assertThat(response.getAcknowledgedAt()).isNotNull();
    }

    @Test
    @DisplayName("Người khác / id không tồn tại -> 404 (ExecutorAssignmentNotFoundException)")
    void acknowledge_wrongUserOrUnknownIdIsNotFound() {
        assertThatThrownBy(() -> service.acknowledgeActivation(primary.getId(), "someone-else@example.test"))
                .isInstanceOf(ExecutorAssignmentNotFoundException.class);
        assertThatThrownBy(() -> service.acknowledgeActivation(UUID.randomUUID(), "primary@example.test"))
                .isInstanceOf(ExecutorAssignmentNotFoundException.class);
        assertThatThrownBy(() -> service.acknowledgeActivation(primary.getId(), null))
                .isInstanceOf(ExecutorAssignmentNotFoundException.class);
        verifyNoInteractions(auditLogService);
    }

    @Test
    @DisplayName("Executor chưa ACTIVE hoặc đã REVOKED -> 403 (ExecutorNotActiveException)")
    void acknowledge_inactiveOrRevokedIsForbidden() {
        primary.setStatus(ExecutorStatus.INACTIVE);
        assertThatThrownBy(() -> service.acknowledgeActivation(primary.getId(), "primary@example.test"))
                .isInstanceOf(ExecutorNotActiveException.class);
        primary.setStatus(ExecutorStatus.REVOKED);
        assertThatThrownBy(() -> service.acknowledgeActivation(primary.getId(), "primary@example.test"))
                .isInstanceOf(ExecutorNotActiveException.class);
    }

    @Test
    @DisplayName("DMS không ở trạng thái TRIGGERED -> 409 (DmsNotTriggeredException)")
    void acknowledge_requiresTriggeredDms() {
        config.setStatus(DmsStatus.ACTIVE);
        assertThatThrownBy(() -> service.acknowledgeActivation(primary.getId(), "primary@example.test"))
                .isInstanceOf(DmsNotTriggeredException.class);
        verifyNoInteractions(auditLogService);
    }

    @Test
    @DisplayName("Backup đang ACTIVE xác nhận được; thông báo tra theo EXECUTOR_ACTIVATED")
    void acknowledge_backupUsesExecutorActivatedNotification() {
        UUID backupUserId = UUID.randomUUID();
        ExecutorAssignment backup = ExecutorAssignment.builder().id(UUID.randomUUID()).owner(primary.getOwner())
                .executorUser(User.builder().id(backupUserId).email("backup@example.test").build())
                .executorType(ExecutorType.BACKUP).status(ExecutorStatus.ACTIVE).activatedAt(now).build();
        when(executorAssignmentRepository.findById(backup.getId())).thenReturn(Optional.of(backup));

        service.acknowledgeActivation(backup.getId(), "backup@example.test");

        assertThat(backup.getAcknowledgedAt()).isNotNull();
        verify(notificationRepository).findFirstByRecipientIdAndTypeAndReferenceTypeAndReferenceIdOrderByCreatedAtDesc(
                eq(backupUserId), eq(NotificationType.EXECUTOR_ACTIVATED), eq("DmsConfig"), eq(config.getId()));
    }

    @Test
    @DisplayName("Không có DMS quá hạn -> processOverdueDms không làm gì, processor.trigger() không được gọi")
    void processOverdueDms_emptyList_doesNothing() {
        when(dmsConfigRepository.findAllByStatusAndNextCheckinDueAtBefore(DmsStatus.ACTIVE, now))
                .thenReturn(Collections.emptyList());

        service.processOverdueDms(now);

        verify(processor, never()).trigger(any(), any());
    }
}
