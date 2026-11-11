package com.ltld.app.legacyvault.service.dmsservice;

import com.ltld.app.legacyvault.dto.dmsdto.DmsCheckinResponse;
import com.ltld.app.legacyvault.dto.dmsdto.DmsConfigRequest;
import com.ltld.app.legacyvault.dto.dmsdto.DmsConfigResponse;
import com.ltld.app.legacyvault.entity.DmsCheckin;
import com.ltld.app.legacyvault.entity.DmsConfig;
import com.ltld.app.legacyvault.entity.ExecutorAssignment;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.DmsCheckinType;
import com.ltld.app.legacyvault.enums.DmsStatus;
import com.ltld.app.legacyvault.enums.ExecutorStatus;
import com.ltld.app.legacyvault.enums.ExecutorType;
import com.ltld.app.legacyvault.enums.NotificationChannel;
import com.ltld.app.legacyvault.exception.DmsException;
import com.ltld.app.legacyvault.repository.DmsCheckinRepository;
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
import com.ltld.app.legacyvault.repository.ExecutorAssignmentRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DmsConfigServiceImplTest {

    @Mock private DmsConfigRepository dmsConfigRepository;
    @Mock private DmsCheckinRepository dmsCheckinRepository;
    @Mock private ExecutorAssignmentRepository executorAssignmentRepository;
    @Mock private VaultRepository vaultRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private DmsConfigServiceImpl service;

    private UUID ownerId;
    private User owner;
    private UUID adminId;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        owner = User.builder().id(ownerId).email("owner@example.test").build();
        adminId = UUID.randomUUID();

        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(userRepository.findById(adminId))
                .thenReturn(Optional.of(User.builder().id(adminId).email("admin@example.test").build()));
        when(vaultRepository.existsByOwnerId(ownerId)).thenReturn(true);
        when(dmsConfigRepository.findByOwnerId(ownerId)).thenReturn(Optional.empty());
        // saveAndFlush giả lập DB: gán id cho bản ghi mới, trả lại chính entity.
        when(dmsConfigRepository.saveAndFlush(any(DmsConfig.class))).thenAnswer(inv -> {
            DmsConfig c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(UUID.randomUUID());
            }
            return c;
        });
        when(dmsCheckinRepository.saveAndFlush(any(DmsCheckin.class))).thenAnswer(inv -> inv.getArgument(0));
        // FR-03: mặc định Owner đã có Primary, để bật DMS / mở lại DMS được phép.
        executors(executor(ExecutorType.PRIMARY, ExecutorStatus.INACTIVE));
    }

    /** Stub các dòng Executor của Owner cho cả lần đọc có khóa (upsert/resume) lẫn đọc thường (getConfig...). */
    private void executors(ExecutorAssignment... assignments) {
        when(executorAssignmentRepository.findAllByOwnerIdForUpdate(ownerId)).thenReturn(List.of(assignments));
        when(executorAssignmentRepository.findAllByOwnerId(ownerId)).thenReturn(List.of(assignments));
    }

    private ExecutorAssignment executor(ExecutorType type, ExecutorStatus status) {
        return ExecutorAssignment.builder()
                .id(UUID.randomUUID())
                .owner(owner)
                .executorUser(User.builder().id(UUID.randomUUID()).email(type + "@example.test").build())
                .executorType(type)
                .status(status)
                .version(0L)
                .build();
    }

    private static DmsConfigRequest request(int intervalDays, int gracePeriodDays, int reminderCount) {
        DmsConfigRequest request = new DmsConfigRequest();
        request.setCheckinIntervalDays(intervalDays);
        request.setGracePeriodDays(gracePeriodDays);
        request.setReminderCount(reminderCount);
        return request;
    }

    private DmsConfig existingConfig(DmsStatus status) {
        LocalDateTime lastCheckin = LocalDateTime.of(2026, 1, 1, 8, 0);
        DmsConfig config = DmsConfig.builder()
                .id(UUID.randomUUID())
                .owner(owner)
                .status(status)
                .checkinIntervalDays(30)
                .gracePeriodDays(7)
                .reminderCount(3)
                .backupActivationWaitDays(21)
                .lastCheckinAt(lastCheckin)
                .nextCheckinDueAt(lastCheckin.plusDays(37))
                .nextReminderAt(lastCheckin.plusDays(32))
                .remindersSent(2)
                .version(3L)
                .build();
        when(dmsConfigRepository.findByOwnerId(ownerId)).thenReturn(Optional.of(config));
        // upsertConfig đọc bằng khóa (FR-03).
        when(dmsConfigRepository.findByOwnerIdForUpdate(ownerId)).thenReturn(Optional.of(config));
        return config;
    }

    private DmsConfig savedConfig() {
        ArgumentCaptor<DmsConfig> captor = ArgumentCaptor.forClass(DmsConfig.class);
        verify(dmsConfigRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    /** FR-03: đúng 1 DmsCheckin OWNER_RESPONDED cho cấu hình vừa lưu, occurredAt = mốc bắt đầu chu kỳ mới. */
    private void verifyOwnerRespondedRecorded(DmsConfig saved) {
        ArgumentCaptor<DmsCheckin> checkin = ArgumentCaptor.forClass(DmsCheckin.class);
        verify(dmsCheckinRepository).saveAndFlush(checkin.capture());
        assertThat(checkin.getValue().getDmsConfig()).isSameAs(saved);
        assertThat(checkin.getValue().getCheckinType()).isEqualTo(DmsCheckinType.OWNER_RESPONDED);
        assertThat(checkin.getValue().getOccurredAt()).isEqualTo(saved.getLastCheckinAt());
        assertThat(checkin.getValue().getChannel()).isNull();
    }

    private void verifyNothingWritten() {
        verify(dmsConfigRepository, never()).saveAndFlush(any());
        verifyNoInteractions(dmsCheckinRepository, auditLogService);
    }

    // ---------- upsertConfig ----------

    @Test
    @DisplayName("FR-03: tạo mới -> ACTIVE, EMAIL, chờ Backup 14 ngày, lịch tính từ lúc lưu, ghi DmsCheckin OWNER_RESPONDED, audit DMS_CONFIGURED")
    void upsert_createsNewConfig() {
        LocalDateTime before = LocalDateTime.now();

        DmsConfigService.UpsertResult result = service.upsertConfig(ownerId, request(30, 7, 3));

        LocalDateTime after = LocalDateTime.now();
        assertThat(result.created()).isTrue();
        DmsConfig saved = savedConfig();
        assertThat(saved.getOwner()).isSameAs(owner);
        assertThat(saved.getStatus()).isEqualTo(DmsStatus.ACTIVE);
        assertThat(saved.getCheckinIntervalDays()).isEqualTo(30);
        assertThat(saved.getGracePeriodDays()).isEqualTo(7);
        assertThat(saved.getReminderCount()).isEqualTo(3);
        assertThat(saved.getNotificationChannel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(saved.getBackupActivationWaitDays()).isEqualTo(14);
        assertThat(saved.getRemindersSent()).isZero();

        LocalDateTime start = saved.getLastCheckinAt();
        assertThat(start).isBetween(before, after);
        assertThat(saved.getNextCheckinDueAt()).isEqualTo(start.plusDays(37));   // hạn chót trigger
        assertThat(saved.getNextReminderAt()).isEqualTo(start.plusDays(30));     // lần nhắc đầu = hạn check-in

        DmsConfigResponse response = result.config();
        assertThat(response.getCheckinDueAt()).isEqualTo(start.plusDays(30));
        assertThat(response.getTriggerAt()).isEqualTo(start.plusDays(37));
        assertThat(response.getStatus()).isEqualTo(DmsStatus.ACTIVE);
        assertThat(response.getConfigured()).isTrue();

        verifyOwnerRespondedRecorded(saved);
        InOrder order = inOrder(dmsConfigRepository, dmsCheckinRepository, auditLogService);
        order.verify(dmsConfigRepository).saveAndFlush(saved);
        order.verify(dmsCheckinRepository).saveAndFlush(any(DmsCheckin.class));
        order.verify(auditLogService).log(eq(AuditAction.DMS_CONFIGURED), eq(AuditResult.SUCCESS),
                eq(ownerId), eq("owner@example.test"), isNull(),
                eq(DmsActivationProcessor.ENTITY_DMS_CONFIG), eq(saved.getId().toString()),
                contains("created=true"));
    }

    @Test
    @DisplayName("FR-03: cập nhật -> giữ backupActivationWaitDays, reset remindersSent, bắt đầu chu kỳ mới, ghi DmsCheckin OWNER_RESPONDED")
    void upsert_updatesExistingConfig() {
        DmsConfig existing = existingConfig(DmsStatus.ACTIVE);
        LocalDateTime before = LocalDateTime.now();

        DmsConfigService.UpsertResult result = service.upsertConfig(ownerId, request(60, 10, 2));

        assertThat(result.created()).isFalse();
        DmsConfig saved = savedConfig();
        assertThat(saved).isSameAs(existing);
        assertThat(saved.getCheckinIntervalDays()).isEqualTo(60);
        assertThat(saved.getGracePeriodDays()).isEqualTo(10);
        assertThat(saved.getReminderCount()).isEqualTo(2);
        assertThat(saved.getBackupActivationWaitDays()).isEqualTo(21);
        assertThat(saved.getRemindersSent()).isZero();
        assertThat(saved.getLastCheckinAt()).isAfterOrEqualTo(before);
        assertThat(saved.getNextCheckinDueAt()).isEqualTo(saved.getLastCheckinAt().plusDays(70));
        assertThat(saved.getNextReminderAt()).isEqualTo(saved.getLastCheckinAt().plusDays(60));

        verify(userRepository, never()).findById(any());
        verify(auditLogService).log(eq(AuditAction.DMS_CONFIGURED), eq(AuditResult.SUCCESS),
                eq(ownerId), eq("owner@example.test"), isNull(),
                eq(DmsActivationProcessor.ENTITY_DMS_CONFIG), eq(existing.getId().toString()),
                contains("created=false"));
        verifyOwnerRespondedRecorded(existing);
        InOrder order = inOrder(dmsConfigRepository, dmsCheckinRepository, auditLogService);
        order.verify(dmsConfigRepository).saveAndFlush(existing);
        order.verify(dmsCheckinRepository).saveAndFlush(any(DmsCheckin.class));
        order.verify(auditLogService).log(eq(AuditAction.DMS_CONFIGURED), any(), any(), any(), any(), any(), any(),
                anyString());
    }

    @Test
    @DisplayName("FR-03: Owner chưa có Vault -> 409, không lưu, không audit")
    void upsert_withoutVault_returns409() {
        when(vaultRepository.existsByOwnerId(ownerId)).thenReturn(false);

        assertThatThrownBy(() -> service.upsertConfig(ownerId, request(30, 7, 3)))
                .isInstanceOf(DmsException.class)
                .extracting(e -> ((DmsException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verifyNothingWritten();
    }

    @Test
    @DisplayName("FR-03: DMS đã TRIGGERED -> cấu hình lại bị từ chối 409")
    void upsert_whenTriggered_returns409() {
        DmsConfig existing = existingConfig(DmsStatus.TRIGGERED);
        LocalDateTime deadline = existing.getNextCheckinDueAt();

        assertThatThrownBy(() -> service.upsertConfig(ownerId, request(30, 7, 3)))
                .isInstanceOf(DmsException.class)
                .extracting(e -> ((DmsException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(existing.getStatus()).isEqualTo(DmsStatus.TRIGGERED);
        assertThat(existing.getNextCheckinDueAt()).isEqualTo(deadline);
        verifyNothingWritten();
    }

    @Test
    @DisplayName("Q28 (T5): DMS SUSPENDED -> Owner không tự bật lại bằng lưu cấu hình: 409, không lưu, không audit")
    void upsert_whenSuspended_returns409() {
        DmsConfig existing = existingConfig(DmsStatus.SUSPENDED);
        LocalDateTime deadline = existing.getNextCheckinDueAt();

        assertThatThrownBy(() -> service.upsertConfig(ownerId, request(30, 7, 3)))
                .isInstanceOf(DmsException.class)
                .hasMessage("The Dead Man's Switch has been suspended by an administrator. Please contact an admin.")
                .extracting(e -> ((DmsException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(existing.getStatus()).isEqualTo(DmsStatus.SUSPENDED);
        assertThat(existing.getNextCheckinDueAt()).isEqualTo(deadline);
        verifyNothingWritten();
    }

    @Test
    @DisplayName("Q27 (T7): lưu lại cấu hình bắt đầu chu kỳ mới -> triggerDeferrals về 0")
    void upsert_resetsTriggerDeferrals() {
        DmsConfig existing = existingConfig(DmsStatus.ACTIVE);
        existing.setTriggerDeferrals(3);

        service.upsertConfig(ownerId, request(30, 7, 3));

        assertThat(savedConfig().getTriggerDeferrals()).isZero();
    }

    @Test
    @DisplayName("FR-03: hai yêu cầu tạo đồng thời (trùng owner_id) -> 409 rõ ràng, không ghi DmsCheckin, không audit")
    void upsert_concurrentCreate_returns409() {
        when(dmsConfigRepository.saveAndFlush(any(DmsConfig.class)))
                .thenThrow(new DataIntegrityViolationException("uk_dms_config_owner"));

        assertThatThrownBy(() -> service.upsertConfig(ownerId, request(30, 7, 3)))
                .isInstanceOf(DmsException.class)
                .hasMessageContaining("another request")
                .extracting(e -> ((DmsException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verifyNoInteractions(dmsCheckinRepository, auditLogService);
    }

    @Test
    @DisplayName("FR-03: xung đột @Version khi cập nhật -> để nổi lên cho GlobalExceptionHandler (409), không audit")
    void upsert_optimisticLockConflict_propagates() {
        existingConfig(DmsStatus.ACTIVE);
        when(dmsConfigRepository.saveAndFlush(any(DmsConfig.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(DmsConfig.class, UUID.randomUUID()));

        assertThatThrownBy(() -> service.upsertConfig(ownerId, request(30, 7, 3)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        verifyNoInteractions(dmsCheckinRepository, auditLogService);
    }

    // ---------- FR-03: bật lần đầu phải có Executor chính; lưu lại chỉ cần còn Executor nhận bàn giao ----------

    @Test
    @DisplayName("B-01a: tạo mới khi Owner chưa có Executor nào -> 409, không lưu, không audit")
    void upsert_create_withoutPrimary_returns409() {
        executors();

        assertThatThrownBy(() -> service.upsertConfig(ownerId, request(30, 7, 3)))
                .isInstanceOf(DmsException.class)
                .hasMessage(DmsConfigServiceImpl.NO_PRIMARY_EXECUTOR_MESSAGE)
                .extracting(e -> ((DmsException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verifyNothingWritten();
    }

    @Test
    @DisplayName("B-01a: lưu lại cấu hình khi Primary đã bị thu hồi -> 409, giữ nguyên lịch cũ")
    void upsert_existing_withoutPrimary_returns409() {
        DmsConfig existing = existingConfig(DmsStatus.ACTIVE);
        LocalDateTime deadline = existing.getNextCheckinDueAt();
        executors(executor(ExecutorType.PRIMARY, ExecutorStatus.REVOKED));

        assertThatThrownBy(() -> service.upsertConfig(ownerId, request(60, 10, 2)))
                .isInstanceOf(DmsException.class)
                .hasMessage(DmsConfigServiceImpl.NO_ACTIVATION_TARGET_MESSAGE)
                .extracting(e -> ((DmsException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(existing.getCheckinIntervalDays()).isEqualTo(30);
        assertThat(existing.getNextCheckinDueAt()).isEqualTo(deadline);
        verifyNothingWritten();
    }

    @Test
    @DisplayName("B-01a: chỉ có Backup (Primary chưa chỉ định) -> 409, đúng chữ tài liệu: cần Executor chính")
    void upsert_onlyBackup_returns409() {
        executors(executor(ExecutorType.BACKUP, ExecutorStatus.INACTIVE));

        assertThatThrownBy(() -> service.upsertConfig(ownerId, request(30, 7, 3)))
                .isInstanceOf(DmsException.class)
                .hasMessage(DmsConfigServiceImpl.NO_PRIMARY_EXECUTOR_MESSAGE)
                .extracting(e -> ((DmsException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verifyNothingWritten();
    }

    @Test
    @DisplayName("B-01a: Primary REVOKED dù Backup còn -> 409")
    void upsert_primaryRevokedWithBackup_returns409() {
        executors(executor(ExecutorType.PRIMARY, ExecutorStatus.REVOKED),
                executor(ExecutorType.BACKUP, ExecutorStatus.INACTIVE));

        assertThatThrownBy(() -> service.upsertConfig(ownerId, request(30, 7, 3)))
                .isInstanceOf(DmsException.class)
                .hasMessage(DmsConfigServiceImpl.NO_PRIMARY_EXECUTOR_MESSAGE);
        verifyNothingWritten();
    }

    @Test
    @DisplayName("B-01a: có Primary -> lưu được, hasUsableExecutor=true; khóa DmsConfig rồi mới khóa Executor, rồi mới ghi")
    void upsert_withPrimary_locksDmsConfigThenExecutors() {
        DmsConfigService.UpsertResult result = service.upsertConfig(ownerId, request(30, 7, 3));

        assertThat(result.config().getHasUsableExecutor()).isTrue();
        InOrder order = inOrder(dmsConfigRepository, executorAssignmentRepository);
        order.verify(dmsConfigRepository).findByOwnerIdForUpdate(ownerId);
        order.verify(executorAssignmentRepository).findAllByOwnerIdForUpdate(ownerId);
        order.verify(dmsConfigRepository).saveAndFlush(any(DmsConfig.class));
        verify(dmsConfigRepository, never()).findByOwnerId(any());
    }

    @Test
    @DisplayName("FR-03: đã có cấu hình, Primary đã thu hồi nhưng còn Backup INACTIVE -> lưu lại được")
    void upsert_existing_onlyBackupInactive_allowed() {
        DmsConfig existing = existingConfig(DmsStatus.ACTIVE);
        executors(executor(ExecutorType.PRIMARY, ExecutorStatus.REVOKED),
                executor(ExecutorType.BACKUP, ExecutorStatus.INACTIVE));

        DmsConfigService.UpsertResult result = service.upsertConfig(ownerId, request(60, 10, 2));

        assertThat(result.created()).isFalse();
        assertThat(savedConfig()).isSameAs(existing);
        assertThat(existing.getCheckinIntervalDays()).isEqualTo(60);
        assertThat(result.config().getHasUsableExecutor()).isTrue();
    }

    @Test
    @DisplayName("FR-03: đã có cấu hình nhưng không còn Executor nào nhận bàn giao -> 409 (câu lưu lại), không ghi")
    void upsert_existing_noActivationTarget_returns409() {
        DmsConfig existing = existingConfig(DmsStatus.ACTIVE);
        LocalDateTime deadline = existing.getNextCheckinDueAt();
        executors(executor(ExecutorType.PRIMARY, ExecutorStatus.REVOKED),
                executor(ExecutorType.BACKUP, ExecutorStatus.REVOKED));

        assertThatThrownBy(() -> service.upsertConfig(ownerId, request(60, 10, 2)))
                .isInstanceOf(DmsException.class)
                .hasMessage(DmsConfigServiceImpl.NO_ACTIVATION_TARGET_MESSAGE)
                .extracting(e -> ((DmsException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(existing.getNextCheckinDueAt()).isEqualTo(deadline);
        verifyNothingWritten();
    }

    // ---------- checkIn ----------

    @Test
    @DisplayName("FR-03: check-in -> lùi hạn trigger theo cấu hình đã lưu, reset nhắc, lưu DmsCheckin OWNER_RESPONDED, audit DMS_CHECKIN")
    void checkIn_pushesTriggerDeadline() {
        DmsConfig existing = existingConfig(DmsStatus.ACTIVE);
        LocalDateTime oldDeadline = existing.getNextCheckinDueAt();
        LocalDateTime before = LocalDateTime.now();

        DmsConfigResponse response = service.checkIn(ownerId);

        LocalDateTime now = existing.getLastCheckinAt();
        assertThat(now).isAfterOrEqualTo(before);
        assertThat(existing.getNextCheckinDueAt()).isEqualTo(now.plusDays(37)).isAfter(oldDeadline);
        assertThat(existing.getNextReminderAt()).isEqualTo(now.plusDays(30));
        assertThat(existing.getRemindersSent()).isZero();
        assertThat(existing.getStatus()).isEqualTo(DmsStatus.ACTIVE);
        assertThat(response.getTriggerAt()).isEqualTo(now.plusDays(37));
        assertThat(response.getCheckinDueAt()).isEqualTo(now.plusDays(30));

        ArgumentCaptor<DmsCheckin> checkin = ArgumentCaptor.forClass(DmsCheckin.class);
        InOrder order = inOrder(dmsConfigRepository, dmsCheckinRepository, auditLogService);
        order.verify(dmsConfigRepository).saveAndFlush(existing);
        order.verify(dmsCheckinRepository).saveAndFlush(checkin.capture());
        order.verify(auditLogService).log(eq(AuditAction.DMS_CHECKIN), eq(AuditResult.SUCCESS),
                eq(ownerId), eq("owner@example.test"), isNull(),
                eq(DmsActivationProcessor.ENTITY_DMS_CONFIG), eq(existing.getId().toString()), anyString());
        assertThat(checkin.getValue().getDmsConfig()).isSameAs(existing);
        assertThat(checkin.getValue().getCheckinType()).isEqualTo(DmsCheckinType.OWNER_RESPONDED);
        assertThat(checkin.getValue().getOccurredAt()).isEqualTo(now);
        assertThat(checkin.getValue().getChannel()).isNull();
    }

    @Test
    @DisplayName("FR-03: check-in khi DMS đã TRIGGERED -> 409, không hủy được quy trình bàn giao")
    void checkIn_whenTriggered_returns409() {
        DmsConfig existing = existingConfig(DmsStatus.TRIGGERED);
        LocalDateTime deadline = existing.getNextCheckinDueAt();

        assertThatThrownBy(() -> service.checkIn(ownerId))
                .isInstanceOf(DmsException.class)
                .extracting(e -> ((DmsException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(existing.getStatus()).isEqualTo(DmsStatus.TRIGGERED);
        assertThat(existing.getNextCheckinDueAt()).isEqualTo(deadline);
        verifyNothingWritten();
    }

    @Test
    @DisplayName("FR-03: check-in khi DMS SUSPENDED -> 409")
    void checkIn_whenSuspended_returns409() {
        existingConfig(DmsStatus.SUSPENDED);

        assertThatThrownBy(() -> service.checkIn(ownerId))
                .isInstanceOf(DmsException.class)
                .hasMessage("The Dead Man's Switch has been suspended by an administrator. Please contact an admin.")
                .extracting(e -> ((DmsException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verifyNothingWritten();
    }

    @Test
    @DisplayName("Q27 (T7): check-in bắt đầu chu kỳ mới -> triggerDeferrals về 0")
    void checkIn_resetsTriggerDeferrals() {
        DmsConfig existing = existingConfig(DmsStatus.ACTIVE);
        existing.setTriggerDeferrals(2);

        service.checkIn(ownerId);

        assertThat(existing.getTriggerDeferrals()).isZero();
        assertThat(existing.getRemindersSent()).isZero();
    }

    @Test
    @DisplayName("FR-03: check-in với cấu hình cũ chưa có thời gian chờ -> 409 yêu cầu lưu lại cấu hình")
    void checkIn_legacyConfigWithoutGracePeriod_returns409() {
        existingConfig(DmsStatus.ACTIVE).setGracePeriodDays(null);

        assertThatThrownBy(() -> service.checkIn(ownerId))
                .isInstanceOf(DmsException.class)
                .extracting(e -> ((DmsException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verifyNothingWritten();
    }

    @Test
    @DisplayName("FR-03: check-in khi chưa cấu hình -> 404")
    void checkIn_withoutConfig_returns404() {
        assertThatThrownBy(() -> service.checkIn(ownerId))
                .isInstanceOf(DmsException.class)
                .extracting(e -> ((DmsException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        verifyNothingWritten();
    }

    // ---------- getConfig ----------

    // Cột trống = chưa có dòng cho vị trí đó. Chưa cấu hình: điều kiện bật DMS lần đầu (Primary chưa REVOKED),
    // nên chỉ có Backup INACTIVE vẫn là false (khác với khi đã cấu hình).
    @ParameterizedTest(name = "Primary={0}, Backup={1} -> hasUsableExecutor={2}")
    @CsvSource({
            "INACTIVE, , true",
            "ACTIVE, INACTIVE, true",
            "REVOKED, INACTIVE, false",
            ", INACTIVE, false",
            ", , false"
    })
    @DisplayName("FR-03: xem cấu hình khi chưa có -> configured=false, hasUsableExecutor = có Primary chưa REVOKED, còn lại null")
    void getConfig_withoutConfig_returnsNotConfigured(ExecutorStatus primary, ExecutorStatus backup, boolean expected) {
        List<ExecutorAssignment> rows = new ArrayList<>();
        if (primary != null) rows.add(executor(ExecutorType.PRIMARY, primary));
        if (backup != null) rows.add(executor(ExecutorType.BACKUP, backup));
        executors(rows.toArray(ExecutorAssignment[]::new));

        DmsConfigResponse response = service.getConfig(ownerId);

        assertThat(response.getConfigured()).isFalse();
        assertThat(response.getHasUsableExecutor()).isEqualTo(expected);
        assertThat(response).usingRecursiveComparison()
                .ignoringFields("configured", "hasUsableExecutor")
                .isEqualTo(new DmsConfigResponse());
        verifyNothingWritten();
    }

    @Test
    @DisplayName("FR-03: xem cấu hình đã có -> configured=true")
    void getConfig_withConfig_returnsConfigured() {
        existingConfig(DmsStatus.ACTIVE);

        assertThat(service.getConfig(ownerId).getConfigured()).isTrue();
    }

    @Test
    @DisplayName("FR-03: xem cấu hình -> checkinDueAt = lastCheckinAt + chu kỳ, triggerAt = nextCheckinDueAt")
    void getConfig_mapsSchedule() {
        DmsConfig existing = existingConfig(DmsStatus.ACTIVE);

        DmsConfigResponse response = service.getConfig(ownerId);

        assertThat(response.getStatus()).isEqualTo(DmsStatus.ACTIVE);
        assertThat(response.getCheckinIntervalDays()).isEqualTo(30);
        assertThat(response.getGracePeriodDays()).isEqualTo(7);
        assertThat(response.getReminderCount()).isEqualTo(3);
        assertThat(response.getLastCheckinAt()).isEqualTo(existing.getLastCheckinAt());
        assertThat(response.getCheckinDueAt()).isEqualTo(existing.getLastCheckinAt().plusDays(30));
        assertThat(response.getTriggerAt()).isEqualTo(existing.getNextCheckinDueAt());
        assertThat(response.getNextReminderAt()).isEqualTo(existing.getNextReminderAt());
        assertThat(response.getRemindersSent()).isEqualTo(2);
        verifyNothingWritten();
    }

    // Cột trống = chưa có dòng cho vị trí đó. Cùng quy tắc chọn người với DmsActivationProcessor.trigger.
    @ParameterizedTest(name = "Primary={0}, Backup={1} -> hasUsableExecutor={2}")
    @CsvSource({
            "INACTIVE, , true",
            "ACTIVE, , true",
            "INACTIVE, INACTIVE, true",
            "REVOKED, INACTIVE, true",
            "REVOKED, ACTIVE, false",
            "REVOKED, REVOKED, false",
            ", INACTIVE, true",
            ", ACTIVE, false",
            ", , false"
    })
    @DisplayName("B-01a: hasUsableExecutor tính như trigger chọn người (Primary chưa REVOKED, nếu không thì Backup INACTIVE)")
    void getConfig_hasUsableExecutor(ExecutorStatus primary, ExecutorStatus backup, boolean expected) {
        existingConfig(DmsStatus.ACTIVE);
        List<ExecutorAssignment> rows = new ArrayList<>();
        if (primary != null) rows.add(executor(ExecutorType.PRIMARY, primary));
        if (backup != null) rows.add(executor(ExecutorType.BACKUP, backup));
        executors(rows.toArray(ExecutorAssignment[]::new));

        assertThat(service.getConfig(ownerId).getHasUsableExecutor()).isEqualTo(expected);
    }

    @Test
    @DisplayName("FR-03: DMS đã TRIGGERED -> hasUsableExecutor = null (bàn giao đã bắt đầu)")
    void getConfig_triggered_hasUsableExecutorNull() {
        existingConfig(DmsStatus.TRIGGERED);
        executors(executor(ExecutorType.PRIMARY, ExecutorStatus.ACTIVE));

        assertThat(service.getConfig(ownerId).getHasUsableExecutor()).isNull();
    }

    // ---------- getCheckins ----------

    @Test
    @DisplayName("FR-03: lịch sử check-in khi chưa cấu hình -> trang rỗng, không đọc dms_checkins")
    void getCheckins_withoutConfig_returnsEmptyPage() {
        Page<DmsCheckinResponse> page = service.getCheckins(ownerId, PageRequest.of(0, 20));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
        verifyNoInteractions(dmsCheckinRepository);
    }

    @Test
    @DisplayName("FR-03: lịch sử check-in -> theo DmsConfig của chính Owner, mới nhất trước (bỏ sort của người gọi), map đủ trường")
    void getCheckins_mapsRowsNewestFirst() {
        DmsConfig config = existingConfig(DmsStatus.ACTIVE);
        LocalDateTime at = LocalDateTime.of(2026, 10, 1, 9, 0);
        DmsCheckin reminder = DmsCheckin.builder().dmsConfig(config).checkinType(DmsCheckinType.REMINDER_SENT)
                .channel(NotificationChannel.EMAIL).occurredAt(at).build();
        when(dmsCheckinRepository.findByDmsConfigId(eq(config.getId()), any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<>(List.of(reminder), inv.getArgument(1), 3));

        Page<DmsCheckinResponse> page = service.getCheckins(ownerId,
                PageRequest.of(1, 1, Sort.by(Sort.Direction.ASC, "occurredAt")));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(dmsCheckinRepository).findByDmsConfigId(eq(config.getId()), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(1);
        assertThat(pageable.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id")));
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).singleElement().satisfies(r -> {
            assertThat(r.getCheckinType()).isEqualTo(DmsCheckinType.REMINDER_SENT);
            assertThat(r.getChannel()).isEqualTo(NotificationChannel.EMAIL);
            assertThat(r.getOccurredAt()).isEqualTo(at);
        });
    }

    // ---------- assertHandoverNotStarted (O1) ----------

    @Test
    @DisplayName("O1: DMS TRIGGERED -> DmsException 409, chỉ đọc có khóa, không ghi gì")
    void assertHandoverNotStarted_whenTriggered_throws409() {
        lockedConfig(DmsStatus.TRIGGERED);

        assertThatThrownBy(() -> service.assertHandoverNotStarted(ownerId))
                .isInstanceOf(DmsException.class)
                .hasMessage(DmsConfigServiceImpl.HANDOVER_STARTED_MESSAGE)
                .extracting(e -> ((DmsException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verifyLockedLookupOnly();
    }

    @Test
    @DisplayName("O1: chưa có DmsConfig -> cho qua")
    void assertHandoverNotStarted_withoutConfig_passes() {
        when(dmsConfigRepository.findByOwnerIdForUpdate(ownerId)).thenReturn(Optional.empty());

        assertThatCode(() -> service.assertHandoverNotStarted(ownerId)).doesNotThrowAnyException();
        verifyLockedLookupOnly();
    }

    @ParameterizedTest
    @EnumSource(value = DmsStatus.class, names = "TRIGGERED", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("O1: DMS chưa kích hoạt (mọi trạng thái khác TRIGGERED) -> cho qua")
    void assertHandoverNotStarted_whenNotTriggered_passes(DmsStatus status) {
        lockedConfig(status);

        assertThatCode(() -> service.assertHandoverNotStarted(ownerId)).doesNotThrowAnyException();
        verifyLockedLookupOnly();
    }

    // ---------- suspendByAdmin / resumeByAdmin (O5) ----------

    @Test
    @DisplayName("O5: Admin tạm dừng DMS ACTIVE -> SUSPENDED, giữ lịch, audit DMS_SUSPENDED với actor là Admin")
    void suspendByAdmin_active_suspendsAndAuditsDmsSuspended() {
        DmsConfig existing = lockedConfig(DmsStatus.ACTIVE);
        LocalDateTime deadline = existing.getNextCheckinDueAt();
        LocalDateTime nextReminder = existing.getNextReminderAt();

        DmsConfigResponse response = service.suspendByAdmin(ownerId, adminId, "  Owner yêu cầu tạm dừng  ");

        assertThat(response.getStatus()).isEqualTo(DmsStatus.SUSPENDED);
        assertThat(existing.getStatus()).isEqualTo(DmsStatus.SUSPENDED);
        assertThat(existing.getNextCheckinDueAt()).isEqualTo(deadline);
        assertThat(existing.getNextReminderAt()).isEqualTo(nextReminder);
        assertThat(existing.getRemindersSent()).isEqualTo(2);

        ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
        InOrder order = inOrder(dmsConfigRepository, auditLogService);
        order.verify(dmsConfigRepository).saveAndFlush(existing);
        order.verify(auditLogService).log(eq(AuditAction.DMS_SUSPENDED), eq(AuditResult.SUCCESS),
                eq(adminId), eq("admin@example.test"), isNull(),
                eq(DmsActivationProcessor.ENTITY_DMS_CONFIG), eq(existing.getId().toString()), detail.capture());
        assertThat(detail.getValue())
                .isEqualTo("ownerId=" + ownerId + ", reason=Owner yêu cầu tạm dừng")
                .doesNotContain("owner@example.test");
        verify(dmsConfigRepository, never()).findByOwnerId(any());
        verifyNoInteractions(dmsCheckinRepository);
    }

    @Test
    @DisplayName("O5: tạm dừng không có lý do -> detail chỉ có ownerId")
    void suspendByAdmin_withoutReason_auditsOwnerOnly() {
        DmsConfig existing = lockedConfig(DmsStatus.ACTIVE);

        service.suspendByAdmin(ownerId, adminId, "  ");

        verify(auditLogService).log(eq(AuditAction.DMS_SUSPENDED), eq(AuditResult.SUCCESS),
                eq(adminId), eq("admin@example.test"), isNull(),
                eq(DmsActivationProcessor.ENTITY_DMS_CONFIG), eq(existing.getId().toString()),
                eq("ownerId=" + ownerId));
    }

    @Test
    @DisplayName("O5: tạm dừng DMS đã TRIGGERED -> 409, không ghi gì")
    void suspendByAdmin_whenTriggered_returns409() {
        DmsConfig existing = lockedConfig(DmsStatus.TRIGGERED);

        assertThatThrownBy(() -> service.suspendByAdmin(ownerId, adminId, "lý do"))
                .isInstanceOf(DmsException.class)
                .hasMessage(DmsConfigServiceImpl.SUSPEND_TRIGGERED_MESSAGE)
                .extracting(e -> ((DmsException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(existing.getStatus()).isEqualTo(DmsStatus.TRIGGERED);
        verifyLockedLookupOnly();
    }

    @Test
    @DisplayName("O5: tạm dừng DMS đã SUSPENDED -> 409, không ghi gì")
    void suspendByAdmin_whenAlreadySuspended_returns409() {
        lockedConfig(DmsStatus.SUSPENDED);

        assertThatThrownBy(() -> service.suspendByAdmin(ownerId, adminId, "lý do"))
                .isInstanceOf(DmsException.class)
                .hasMessage(DmsConfigServiceImpl.ALREADY_SUSPENDED_MESSAGE)
                .extracting(e -> ((DmsException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verifyLockedLookupOnly();
    }

    @Test
    @DisplayName("O5: tạm dừng khi chưa cấu hình -> 404")
    void suspendByAdmin_withoutConfig_returns404() {
        when(dmsConfigRepository.findByOwnerIdForUpdate(ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.suspendByAdmin(ownerId, adminId, "lý do"))
                .isInstanceOf(DmsException.class)
                .extracting(e -> ((DmsException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        verifyLockedLookupOnly();
    }

    @Test
    @DisplayName("O5: Admin mở lại DMS SUSPENDED -> ACTIVE, chu kỳ mới từ lúc mở lại, audit DMS_RESUMED")
    void resumeByAdmin_suspended_startsNewCycleAndAuditsDmsResumed() {
        DmsConfig existing = lockedConfig(DmsStatus.SUSPENDED);
        existing.setTriggerDeferrals(1);
        LocalDateTime before = LocalDateTime.now();

        DmsConfigResponse response = service.resumeByAdmin(ownerId, adminId);

        LocalDateTime after = LocalDateTime.now();
        LocalDateTime now = existing.getLastCheckinAt();
        assertThat(now).isBetween(before, after);
        assertThat(existing.getStatus()).isEqualTo(DmsStatus.ACTIVE);
        assertThat(existing.getNextCheckinDueAt()).isEqualTo(now.plusDays(37));
        assertThat(existing.getNextReminderAt()).isEqualTo(now.plusDays(30));
        assertThat(existing.getRemindersSent()).isZero();
        assertThat(existing.getTriggerDeferrals()).isZero();
        assertThat(response.getStatus()).isEqualTo(DmsStatus.ACTIVE);
        assertThat(response.getTriggerAt()).isEqualTo(now.plusDays(37));

        InOrder order = inOrder(dmsConfigRepository, auditLogService);
        order.verify(dmsConfigRepository).saveAndFlush(existing);
        order.verify(auditLogService).log(eq(AuditAction.DMS_RESUMED), eq(AuditResult.SUCCESS),
                eq(adminId), eq("admin@example.test"), isNull(),
                eq(DmsActivationProcessor.ENTITY_DMS_CONFIG), eq(existing.getId().toString()),
                eq("ownerId=" + ownerId + ", nextCheckinDueAt=" + now.plusDays(37)));
        verify(dmsConfigRepository, never()).findByOwnerId(any());
        // Owner không phản hồi nên không ghi DmsCheckin.
        verifyNoInteractions(dmsCheckinRepository);
    }

    @ParameterizedTest
    @EnumSource(value = DmsStatus.class, names = "SUSPENDED", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("O5: mở lại DMS không ở trạng thái SUSPENDED -> 409, không ghi gì")
    void resumeByAdmin_whenNotSuspended_returns409(DmsStatus status) {
        lockedConfig(status);

        assertThatThrownBy(() -> service.resumeByAdmin(ownerId, adminId))
                .isInstanceOf(DmsException.class)
                .hasMessage(DmsConfigServiceImpl.NOT_SUSPENDED_MESSAGE)
                .extracting(e -> ((DmsException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verifyLockedLookupOnly();
    }

    @Test
    @DisplayName("O5: mở lại với cấu hình chưa đầy đủ -> 409, vẫn SUSPENDED")
    void resumeByAdmin_incompleteConfig_returns409() {
        DmsConfig existing = lockedConfig(DmsStatus.SUSPENDED);
        existing.setGracePeriodDays(null);

        assertThatThrownBy(() -> service.resumeByAdmin(ownerId, adminId))
                .isInstanceOf(DmsException.class)
                .hasMessage(DmsConfigServiceImpl.INCOMPLETE_CONFIG_MESSAGE)
                .extracting(e -> ((DmsException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(existing.getStatus()).isEqualTo(DmsStatus.SUSPENDED);
        verifyLockedLookupOnly();
    }

    @Test
    @DisplayName("O5: mở lại khi chưa cấu hình -> 404")
    void resumeByAdmin_withoutConfig_returns404() {
        when(dmsConfigRepository.findByOwnerIdForUpdate(ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resumeByAdmin(ownerId, adminId))
                .isInstanceOf(DmsException.class)
                .extracting(e -> ((DmsException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        verifyLockedLookupOnly();
    }

    @Test
    @DisplayName("B-01a (O5): mở lại khi Owner đã thu hồi hết Executor lúc DMS tạm dừng -> 409, vẫn SUSPENDED, không ghi")
    void resumeByAdmin_withoutPrimary_returns409() {
        DmsConfig existing = lockedConfig(DmsStatus.SUSPENDED);
        executors(executor(ExecutorType.PRIMARY, ExecutorStatus.REVOKED),
                executor(ExecutorType.BACKUP, ExecutorStatus.REVOKED));

        assertThatThrownBy(() -> service.resumeByAdmin(ownerId, adminId))
                .isInstanceOf(DmsException.class)
                .hasMessage(DmsConfigServiceImpl.RESUME_NO_ACTIVATION_TARGET_MESSAGE)
                .extracting(e -> ((DmsException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(existing.getStatus()).isEqualTo(DmsStatus.SUSPENDED);
        verify(executorAssignmentRepository).findAllByOwnerIdForUpdate(ownerId);
        verifyLockedLookupOnly();
    }

    @Test
    @DisplayName("B-01a (O5): mở lại có Primary -> khóa DmsConfig rồi mới khóa Executor, hasUsableExecutor=true")
    void resumeByAdmin_withPrimary_locksDmsConfigThenExecutors() {
        lockedConfig(DmsStatus.SUSPENDED);

        DmsConfigResponse response = service.resumeByAdmin(ownerId, adminId);

        assertThat(response.getHasUsableExecutor()).isTrue();
        InOrder order = inOrder(dmsConfigRepository, executorAssignmentRepository);
        order.verify(dmsConfigRepository).findByOwnerIdForUpdate(ownerId);
        order.verify(executorAssignmentRepository).findAllByOwnerIdForUpdate(ownerId);
        order.verify(dmsConfigRepository).saveAndFlush(any(DmsConfig.class));
    }

    @Test
    @DisplayName("FR-03: mở lại khi Primary đã thu hồi nhưng còn Backup INACTIVE -> ACTIVE, audit DMS_RESUMED")
    void resumeByAdmin_onlyBackupInactive_allowed() {
        DmsConfig existing = lockedConfig(DmsStatus.SUSPENDED);
        executors(executor(ExecutorType.PRIMARY, ExecutorStatus.REVOKED),
                executor(ExecutorType.BACKUP, ExecutorStatus.INACTIVE));

        DmsConfigResponse response = service.resumeByAdmin(ownerId, adminId);

        assertThat(existing.getStatus()).isEqualTo(DmsStatus.ACTIVE);
        assertThat(response.getHasUsableExecutor()).isTrue();
        verify(dmsConfigRepository).saveAndFlush(existing);
        verify(auditLogService).log(eq(AuditAction.DMS_RESUMED), eq(AuditResult.SUCCESS),
                eq(adminId), eq("admin@example.test"), isNull(),
                eq(DmsActivationProcessor.ENTITY_DMS_CONFIG), eq(existing.getId().toString()), anyString());
    }

    @Test
    @DisplayName("FR-03: mở lại khi Owner không còn Executor nào -> 409 (câu mở lại), vẫn SUSPENDED, không ghi")
    void resumeByAdmin_noActivationTarget_returns409() {
        DmsConfig existing = lockedConfig(DmsStatus.SUSPENDED);
        executors();

        assertThatThrownBy(() -> service.resumeByAdmin(ownerId, adminId))
                .isInstanceOf(DmsException.class)
                .hasMessage(DmsConfigServiceImpl.RESUME_NO_ACTIVATION_TARGET_MESSAGE)
                .extracting(e -> ((DmsException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(existing.getStatus()).isEqualTo(DmsStatus.SUSPENDED);
        verifyLockedLookupOnly();
    }

    private DmsConfig lockedConfig(DmsStatus status) {
        DmsConfig config = existingConfig(status);
        when(dmsConfigRepository.findByOwnerIdForUpdate(ownerId)).thenReturn(Optional.of(config));
        return config;
    }

    // Phải đọc bằng khóa PESSIMISTIC_FORCE_INCREMENT, không phải findByOwnerId thường.
    private void verifyLockedLookupOnly() {
        verify(dmsConfigRepository).findByOwnerIdForUpdate(ownerId);
        verify(dmsConfigRepository, never()).findByOwnerId(any());
        verifyNothingWritten();
    }
}
