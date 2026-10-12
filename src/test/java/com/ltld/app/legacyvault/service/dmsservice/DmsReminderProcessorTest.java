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
    @DisplayName("FR-03: tới lần nhắc đầu -> remindersSent 1, lần kế +56h, ghi DmsCheckin REMINDER_SENT, trả mail 1/3")
    void dueReminder_incrementsCounterAndSchedulesNext() {
        LocalDateTime now = DUE.plusMinutes(1);

        Optional<DmsReminderProcessor.ReminderMail> mail = processor.prepareReminder(config.getId(), now);

        assertThat(config.getRemindersSent()).isEqualTo(1);
        assertThat(config.getNextReminderAt()).isEqualTo(DUE.plusHours(56));
        assertThat(config.getNextCheckinDueAt()).isEqualTo(TRIGGER);   // nhắc không lùi hạn trigger
        verify(dmsConfigRepository).saveAndFlush(config);

        DmsCheckin checkin = savedCheckin();
        assertThat(checkin.getDmsConfig()).isSameAs(config);
        assertThat(checkin.getCheckinType()).isEqualTo(DmsCheckinType.REMINDER_SENT);
        assertThat(checkin.getChannel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(checkin.getOccurredAt()).isEqualTo(now);

        assertThat(mail).contains(new DmsReminderProcessor.ReminderMail("owner@example.test", DUE, TRIGGER, 1, 3));
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
        assertThat(savedCheckin().getOccurredAt()).isEqualTo(now);   // đúng một DmsCheckin
        assertThat(mail).get().extracting(DmsReminderProcessor.ReminderMail::reminderNumber).isEqualTo(3);
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
    @DisplayName("FR-03: đã qua thời điểm kích hoạt -> không gửi mail, dừng nhắc, để FR-07 trigger")
    void pastTrigger_stopsRemindingWithoutMail() {
        Optional<DmsReminderProcessor.ReminderMail> mail = processor.prepareReminder(config.getId(), TRIGGER);

        assertThat(mail).isEmpty();
        assertThat(config.getRemindersSent()).isEqualTo(3);
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
}
