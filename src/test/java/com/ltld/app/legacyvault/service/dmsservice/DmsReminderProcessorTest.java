package com.ltld.app.legacyvault.service.dmsservice;

import com.ltld.app.legacyvault.entity.DmsCheckin;
import com.ltld.app.legacyvault.entity.DmsConfig;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.DmsCheckinType;
import com.ltld.app.legacyvault.enums.DmsStatus;
import com.ltld.app.legacyvault.enums.NotificationChannel;
import com.ltld.app.legacyvault.repository.DmsCheckinRepository;
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DmsReminderProcessorTest {

    // Chu kỳ 30 ngày, chờ 7 ngày, 3 lần nhắc: nhắc tại DUE, DUE+56h, DUE+112h; kích hoạt tại DUE+7 ngày.
    private static final LocalDateTime LAST_CHECKIN = LocalDateTime.of(2026, 10, 1, 9, 0);
    private static final LocalDateTime DUE = LAST_CHECKIN.plusDays(30);
    private static final LocalDateTime TRIGGER = DUE.plusDays(7);

    @Mock private DmsConfigRepository dmsConfigRepository;
    @Mock private DmsCheckinRepository dmsCheckinRepository;

    @InjectMocks
    private DmsReminderProcessor processor;

    private DmsConfig config;

    @BeforeEach
    void setUp() {
        config = DmsConfig.builder()
                .id(UUID.randomUUID())
                .owner(User.builder().id(UUID.randomUUID()).email("owner@example.test").build())
                .status(DmsStatus.ACTIVE)
                .checkinIntervalDays(30)
                .gracePeriodDays(7)
                .reminderCount(3)
                .lastCheckinAt(LAST_CHECKIN)
                .nextCheckinDueAt(TRIGGER)
                .nextReminderAt(DUE)
                .remindersSent(0)
                .build();
        when(dmsConfigRepository.findById(config.getId())).thenReturn(Optional.of(config));
    }

    private DmsCheckin savedCheckin() {
        ArgumentCaptor<DmsCheckin> captor = ArgumentCaptor.forClass(DmsCheckin.class);
        verify(dmsCheckinRepository).save(captor.capture());
        return captor.getValue();
    }

    private void verifyNothingWritten() {
        verify(dmsConfigRepository, never()).saveAndFlush(any());
        verifyNoInteractions(dmsCheckinRepository);
    }

    @Test
    @DisplayName("FR-03: tới lần nhắc đầu -> remindersSent 1, lần kế +56h, trả mail 1/3; chưa ghi REMINDER_SENT trước khi gửi")
    void dueReminder_incrementsCounterAndSchedulesNext() {
        LocalDateTime now = DUE.plusMinutes(1);

        Optional<DmsReminderProcessor.ReminderMail> mail = processor.prepareReminder(config.getId(), now);

        assertThat(config.getRemindersSent()).isEqualTo(1);
        assertThat(config.getNextReminderAt()).isEqualTo(DUE.plusHours(56));
        assertThat(config.getNextCheckinDueAt()).isEqualTo(TRIGGER);   // nhắc không lùi hạn trigger
        verify(dmsConfigRepository).saveAndFlush(config);
        verifyNoInteractions(dmsCheckinRepository);   // M1: REMINDER_SENT ghi sau khi gửi (recordReminderSent)

        assertThat(mail).contains(new DmsReminderProcessor.ReminderMail("owner@example.test", DUE, TRIGGER, 1, 3, 0));
    }

    @Test
    @DisplayName("FR-03: chưa tới lần nhắc (Owner vừa check-in) -> không làm gì")
    void notYetDue_doesNothing() {
        assertThat(processor.prepareReminder(config.getId(), DUE.minusMinutes(1))).isEmpty();

        assertThat(config.getRemindersSent()).isZero();
        verifyNothingWritten();
    }

    @Test
    @DisplayName("FR-03: DMS không còn ACTIVE (vừa TRIGGERED) -> không làm gì")
    void notActive_doesNothing() {
        config.setStatus(DmsStatus.TRIGGERED);

        assertThat(processor.prepareReminder(config.getId(), DUE.plusMinutes(1))).isEmpty();

        verifyNothingWritten();
    }

    @Test
    @DisplayName("FR-03: config không còn tồn tại -> không làm gì")
    void missingConfig_doesNothing() {
        assertThat(processor.prepareReminder(UUID.randomUUID(), DUE.plusMinutes(1))).isEmpty();

        verifyNothingWritten();
    }

    @Test
    @DisplayName("FR-03: server tắt lâu, cả 3 lần nhắc đã quá hạn -> nhảy tới lần 3, chỉ một mail, hết lần nhắc")
    void severalRemindersOverdue_jumpsToLatestAndSendsOneMail() {
        LocalDateTime now = DUE.plusHours(113);

        Optional<DmsReminderProcessor.ReminderMail> mail = processor.prepareReminder(config.getId(), now);

        assertThat(config.getRemindersSent()).isEqualTo(3);
        assertThat(config.getNextReminderAt()).isNull();
        verifyNoInteractions(dmsCheckinRepository);
        assertThat(mail).get().extracting(DmsReminderProcessor.ReminderMail::reminderNumber).isEqualTo(3);
        assertThat(mail).get().extracting(DmsReminderProcessor.ReminderMail::previousRemindersSent).isEqualTo(0);
    }

    @Test
    @DisplayName("FR-03: đã nhắc lần 1, quá hạn lần 2 nhưng chưa tới lần 3 -> nhắc lần 2, lần kế là lần 3")
    void secondReminderOverdue_jumpsToSecond() {
        config.setRemindersSent(1);
        config.setNextReminderAt(DUE.plusHours(56));

        Optional<DmsReminderProcessor.ReminderMail> mail = processor.prepareReminder(config.getId(), DUE.plusHours(60));

        assertThat(config.getRemindersSent()).isEqualTo(2);
        assertThat(config.getNextReminderAt()).isEqualTo(DUE.plusHours(112));
        assertThat(mail).get().extracting(DmsReminderProcessor.ReminderMail::reminderNumber).isEqualTo(2);
    }

    @Test
    @DisplayName("FR-03: lần nhắc cuối -> nextReminderAt = null")
    void lastReminder_clearsNextReminderAt() {
        config.setRemindersSent(2);
        config.setNextReminderAt(DUE.plusHours(112));

        Optional<DmsReminderProcessor.ReminderMail> mail = processor.prepareReminder(config.getId(), DUE.plusHours(112));

        assertThat(config.getRemindersSent()).isEqualTo(3);
        assertThat(config.getNextReminderAt()).isNull();
        verify(dmsConfigRepository).saveAndFlush(config);
        assertThat(mail).get().extracting(DmsReminderProcessor.ReminderMail::reminderNumber).isEqualTo(3);
    }

    @Test
    @DisplayName("FR-03: đã qua thời điểm kích hoạt -> không gửi mail, dừng nhắc, giữ nguyên remindersSent, để FR-07 trigger")
    void pastTrigger_stopsRemindingWithoutMail() {
        config.setRemindersSent(1);
        config.setNextReminderAt(DUE.plusHours(56));

        Optional<DmsReminderProcessor.ReminderMail> mail = processor.prepareReminder(config.getId(), TRIGGER);

        assertThat(mail).isEmpty();
        assertThat(config.getRemindersSent()).isEqualTo(1);
        assertThat(config.getNextReminderAt()).isNull();
        assertThat(config.getStatus()).isEqualTo(DmsStatus.ACTIVE);
        verify(dmsConfigRepository).saveAndFlush(config);
        verifyNoInteractions(dmsCheckinRepository);
    }

    @Test
    @DisplayName("FR-03: nextReminderAt lệch với lịch (không có lần nhắc mới) -> sửa mốc, không gửi")
    void nextReminderAtOutOfSync_resyncsWithoutMail() {
        config.setRemindersSent(1);
        config.setNextReminderAt(DUE);   // lẽ ra phải là DUE+56h

        Optional<DmsReminderProcessor.ReminderMail> mail = processor.prepareReminder(config.getId(), DUE.plusHours(1));

        assertThat(mail).isEmpty();
        assertThat(config.getRemindersSent()).isEqualTo(1);
        assertThat(config.getNextReminderAt()).isEqualTo(DUE.plusHours(56));
        verifyNoInteractions(dmsCheckinRepository);
    }

    @Test
    @DisplayName("Q27 (T8): qua triggerAt mà chưa gửi được lần nào -> remindersSent vẫn 0, nextReminderAt = null")
    void pastTrigger_withoutAnyDeliveredReminder_keepsZero() {
        LocalDateTime now = TRIGGER.plusDays(2);   // server tắt suốt thời gian chờ

        assertThat(processor.prepareReminder(config.getId(), now)).isEmpty();

        assertThat(config.getRemindersSent()).isZero();
        assertThat(config.getNextReminderAt()).isNull();
        verify(dmsConfigRepository).saveAndFlush(config);
        verifyNoInteractions(dmsCheckinRepository);
    }

    @Test
    @DisplayName("Q27: nextCheckinDueAt null -> không gửi, dừng nhắc, giữ nguyên remindersSent")
    void missingTriggerDeadline_stopsRemindingWithoutMail() {
        config.setNextCheckinDueAt(null);

        assertThat(processor.prepareReminder(config.getId(), DUE.plusMinutes(1))).isEmpty();

        assertThat(config.getRemindersSent()).isZero();
        assertThat(config.getNextReminderAt()).isNull();
    }

    @Test
    @DisplayName("Q27 (T6): FR-07 hoãn và lùi nextCheckinDueAt 24 giờ -> vẫn gửi lần nhắc bù với hạn mới")
    void deferredTrigger_sendsCatchUpReminderWithNewDeadline() {
        // FR-07 hoãn tại TRIGGER: lùi hạn chót 24 giờ và đặt nextReminderAt = lúc hoãn.
        LocalDateTime deferredTrigger = TRIGGER.plusHours(24);
        config.setNextCheckinDueAt(deferredTrigger);
        config.setNextReminderAt(TRIGGER);
        config.setTriggerDeferrals(1);
        LocalDateTime now = TRIGGER.plusMinutes(15);

        Optional<DmsReminderProcessor.ReminderMail> mail = processor.prepareReminder(config.getId(), now);

        assertThat(mail).contains(new DmsReminderProcessor.ReminderMail(
                "owner@example.test", DUE, deferredTrigger, 3, 3, 0));
        assertThat(config.getRemindersSent()).isEqualTo(3);
        assertThat(config.getNextReminderAt()).isNull();
        assertThat(config.getNextCheckinDueAt()).isEqualTo(deferredTrigger);
    }

    // ---------- recordReminderSent / releaseReminder (M1) ----------

    @Test
    @DisplayName("M1: recordReminderSent -> ghi DmsCheckin REMINDER_SENT qua EMAIL tại now")
    void recordReminderSent_savesCheckin() {
        LocalDateTime now = DUE.plusMinutes(1);

        processor.recordReminderSent(config.getId(), now);

        DmsCheckin checkin = savedCheckin();
        assertThat(checkin.getDmsConfig()).isSameAs(config);
        assertThat(checkin.getCheckinType()).isEqualTo(DmsCheckinType.REMINDER_SENT);
        assertThat(checkin.getChannel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(checkin.getOccurredAt()).isEqualTo(now);
        verify(dmsConfigRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("M1: recordReminderSent khi config không còn -> không làm gì")
    void recordReminderSent_missingConfig_doesNothing() {
        processor.recordReminderSent(UUID.randomUUID(), DUE);

        verifyNoInteractions(dmsCheckinRepository);
    }

    @Test
    @DisplayName("M1 (T2): releaseReminder hợp lệ -> khôi phục remindersSent, nextReminderAt = now để lượt sau gửi lại")
    void releaseReminder_restoresPreviousCountAndRetriesNow() {
        config.setRemindersSent(2);
        config.setNextReminderAt(DUE.plusHours(112));
        LocalDateTime now = DUE.plusHours(57);

        processor.releaseReminder(config.getId(), 2, 1, now);

        assertThat(config.getRemindersSent()).isEqualTo(1);
        assertThat(config.getNextReminderAt()).isEqualTo(now);
        verify(dmsConfigRepository).saveAndFlush(config);
    }

    @Test
    @DisplayName("M1 (T2): Owner vừa check-in (remindersSent về 0) -> releaseReminder không làm gì")
    void releaseReminder_afterOwnerCheckIn_doesNothing() {
        // Check-in đã bắt đầu chu kỳ mới: remindersSent = 0, nextReminderAt ở chu kỳ sau.
        LocalDateTime newCycleReminder = DUE.plusDays(30);
        config.setRemindersSent(0);
        config.setNextReminderAt(newCycleReminder);

        processor.releaseReminder(config.getId(), 1, 0, DUE.plusMinutes(1));

        assertThat(config.getRemindersSent()).isZero();
        assertThat(config.getNextReminderAt()).isEqualTo(newCycleReminder);
        verify(dmsConfigRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("M1: DMS không còn ACTIVE hoặc config không còn -> releaseReminder không làm gì")
    void releaseReminder_notActiveOrMissing_doesNothing() {
        config.setRemindersSent(1);
        config.setStatus(DmsStatus.TRIGGERED);

        processor.releaseReminder(config.getId(), 1, 0, TRIGGER);
        processor.releaseReminder(UUID.randomUUID(), 1, 0, TRIGGER);

        assertThat(config.getRemindersSent()).isEqualTo(1);
        verify(dmsConfigRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("M1 (T9): lần nhắc nhảy 0 -> 2, gửi lỗi -> release khôi phục remindersSent = 0 (không phải 1), lượt sau gửi lại lần 2")
    void jumpedReminder_releaseRestoresZero() {
        LocalDateTime now = DUE.plusHours(60);   // lần 1 và lần 2 đều đã quá hạn

        Optional<DmsReminderProcessor.ReminderMail> mail = processor.prepareReminder(config.getId(), now);
        assertThat(mail).get().extracting(DmsReminderProcessor.ReminderMail::reminderNumber).isEqualTo(2);
        assertThat(mail).get().extracting(DmsReminderProcessor.ReminderMail::previousRemindersSent).isEqualTo(0);
        assertThat(config.getRemindersSent()).isEqualTo(2);

        processor.releaseReminder(config.getId(), 2, mail.get().previousRemindersSent(), now);

        assertThat(config.getRemindersSent()).isZero();
        assertThat(config.getNextReminderAt()).isEqualTo(now);

        // Lượt quét kế tiếp gửi lại đúng lần nhắc mới nhất.
        Optional<DmsReminderProcessor.ReminderMail> retry = processor.prepareReminder(config.getId(), now.plusMinutes(15));
        assertThat(retry).get().extracting(DmsReminderProcessor.ReminderMail::reminderNumber).isEqualTo(2);
        assertThat(config.getRemindersSent()).isEqualTo(2);
    }
}
