package com.ltld.app.legacyvault.service.dmsservice;

import com.ltld.app.legacyvault.dto.dmsdto.DmsConfigRequest;
import com.ltld.app.legacyvault.dto.dmsdto.DmsConfigResponse;
import com.ltld.app.legacyvault.entity.DmsCheckin;
import com.ltld.app.legacyvault.entity.DmsConfig;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.AuditAction;
import com.ltld.app.legacyvault.enums.AuditResult;
import com.ltld.app.legacyvault.enums.DmsCheckinType;
import com.ltld.app.legacyvault.enums.DmsStatus;
import com.ltld.app.legacyvault.enums.NotificationChannel;
import com.ltld.app.legacyvault.exception.DmsException;
import com.ltld.app.legacyvault.repository.DmsCheckinRepository;
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
import com.ltld.app.legacyvault.repository.UserRepository;
import com.ltld.app.legacyvault.repository.VaultRepository;
import com.ltld.app.legacyvault.service.auditservice.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.LocalDateTime;
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
    @Mock private VaultRepository vaultRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private DmsConfigServiceImpl service;

    private UUID ownerId;
    private User owner;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        owner = User.builder().id(ownerId).email("owner@example.test").build();

        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
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
        return config;
    }

    private DmsConfig savedConfig() {
        ArgumentCaptor<DmsConfig> captor = ArgumentCaptor.forClass(DmsConfig.class);
        verify(dmsConfigRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private void verifyNothingWritten() {
        verify(dmsConfigRepository, never()).saveAndFlush(any());
        verifyNoInteractions(dmsCheckinRepository, auditLogService);
    }

    // ---------- upsertConfig ----------

    @Test
    @DisplayName("FR-03: tạo mới -> ACTIVE, EMAIL, chờ Backup 14 ngày, lịch tính từ lúc lưu, audit DMS_CONFIGURED")
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

        InOrder order = inOrder(dmsConfigRepository, auditLogService);
        order.verify(dmsConfigRepository).saveAndFlush(saved);
        order.verify(auditLogService).log(eq(AuditAction.DMS_CONFIGURED), eq(AuditResult.SUCCESS),
                eq(ownerId), eq("owner@example.test"), isNull(),
                eq(DmsActivationProcessor.ENTITY_DMS_CONFIG), eq(saved.getId().toString()),
                contains("created=true"));
        verifyNoInteractions(dmsCheckinRepository);
    }

    @Test
    @DisplayName("FR-03: cập nhật -> giữ backupActivationWaitDays, reset remindersSent, bắt đầu chu kỳ mới, không tạo DmsCheckin")
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
        verifyNoInteractions(dmsCheckinRepository);
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
                .hasMessage("Dead Man's Switch đang bị quản trị viên tạm dừng. Vui lòng liên hệ Admin.")
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
    @DisplayName("FR-03: hai yêu cầu tạo đồng thời (trùng owner_id) -> 409 rõ ràng, không audit")
    void upsert_concurrentCreate_returns409() {
        when(dmsConfigRepository.saveAndFlush(any(DmsConfig.class)))
                .thenThrow(new DataIntegrityViolationException("uk_dms_config_owner"));

        assertThatThrownBy(() -> service.upsertConfig(ownerId, request(30, 7, 3)))
                .isInstanceOf(DmsException.class)
                .hasMessageContaining("yêu cầu khác")
                .extracting(e -> ((DmsException) e).getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verifyNoInteractions(auditLogService);
    }

    @Test
    @DisplayName("FR-03: xung đột @Version khi cập nhật -> để nổi lên cho GlobalExceptionHandler (409), không audit")
    void upsert_optimisticLockConflict_propagates() {
        existingConfig(DmsStatus.ACTIVE);
        when(dmsConfigRepository.saveAndFlush(any(DmsConfig.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(DmsConfig.class, UUID.randomUUID()));

        assertThatThrownBy(() -> service.upsertConfig(ownerId, request(30, 7, 3)))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        verifyNoInteractions(auditLogService);
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
                .hasMessage("Dead Man's Switch đang bị quản trị viên tạm dừng. Vui lòng liên hệ Admin.")
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

    @Test
    @DisplayName("FR-03: xem cấu hình khi chưa có -> 404")
    void getConfig_withoutConfig_returns404() {
        assertThatThrownBy(() -> service.getConfig(ownerId))
                .isInstanceOf(DmsException.class)
                .extracting(e -> ((DmsException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
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

    private void lockedConfig(DmsStatus status) {
        DmsConfig config = existingConfig(status);
        when(dmsConfigRepository.findByOwnerIdForUpdate(ownerId)).thenReturn(Optional.of(config));
    }

    // Phải đọc bằng khóa PESSIMISTIC_FORCE_INCREMENT, không phải findByOwnerId thường.
    private void verifyLockedLookupOnly() {
        verify(dmsConfigRepository).findByOwnerIdForUpdate(ownerId);
        verify(dmsConfigRepository, never()).findByOwnerId(any());
        verifyNothingWritten();
    }
}
