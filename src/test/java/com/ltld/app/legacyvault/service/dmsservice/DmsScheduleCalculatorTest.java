package com.ltld.app.legacyvault.service.dmsservice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DmsScheduleCalculatorTest {

    private static final LocalDateTime CYCLE_START = LocalDateTime.of(2026, 10, 1, 9, 0);

    private final DmsScheduleCalculator calculator = new DmsScheduleCalculator();

    @Test
    @DisplayName("FR-03: chu kỳ 30 ngày, chờ 7 ngày, 3 lần nhắc -> hạn ngày 30, nhắc +0/+56/+112 giờ, kích hoạt ngày 37")
    void calculate_spreadsRemindersEvenlyOverGracePeriod() {
        DmsSchedule schedule = calculator.calculate(CYCLE_START, 30, 7, 3);

        LocalDateTime due = CYCLE_START.plusDays(30);
        assertThat(schedule.checkinDueAt()).isEqualTo(due);
        assertThat(schedule.triggerAt()).isEqualTo(CYCLE_START.plusDays(37));
        assertThat(schedule.reminderTimes()).containsExactly(due, due.plusHours(56), due.plusHours(112));
    }

    @Test
    @DisplayName("FR-03: chỉ 1 lần nhắc -> nhắc đúng lúc đến hạn check-in")
    void calculate_singleReminderAtCheckinDue() {
        DmsSchedule schedule = calculator.calculate(CYCLE_START, 30, 7, 1);

        assertThat(schedule.reminderTimes()).containsExactly(schedule.checkinDueAt());
    }

    @ParameterizedTest(name = "chu kỳ {0} ngày, chờ {1} ngày, {2} lần nhắc")
    @CsvSource({"1, 1, 1", "30, 7, 3", "30, 1, 10", "365, 90, 10", "7, 3, 7", "1, 1, 10"})
    @DisplayName("FR-03: lần nhắc cuối luôn trước thời điểm kích hoạt, các lần nhắc tăng dần và không trước hạn check-in")
    void calculate_lastReminderAlwaysBeforeTrigger(int intervalDays, int gracePeriodDays, int reminderCount) {
        DmsSchedule schedule = calculator.calculate(CYCLE_START, intervalDays, gracePeriodDays, reminderCount);

        List<LocalDateTime> reminders = schedule.reminderTimes();
        assertThat(reminders).hasSize(reminderCount).isSorted();
        assertThat(reminders.getFirst()).isEqualTo(schedule.checkinDueAt());
        assertThat(reminders.getLast()).isBefore(schedule.triggerAt());
    }

    @ParameterizedTest(name = "chu kỳ {0}, chờ {1}, nhắc {2}")
    @CsvSource({
            "0, 7, 3", "-1, 7, 3",
            "30, 0, 3", "30, -5, 3",
            "30, 7, 0", "30, 7, -2"
    })
    @DisplayName("FR-03: tham số < 1 -> IllegalArgumentException")
    void calculate_rejectsNonPositiveInputs(int intervalDays, int gracePeriodDays, int reminderCount) {
        assertThatThrownBy(() -> calculator.calculate(CYCLE_START, intervalDays, gracePeriodDays, reminderCount))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("FR-03: cycleStart null -> IllegalArgumentException")
    void calculate_rejectsNullCycleStart() {
        assertThatThrownBy(() -> calculator.calculate(null, 30, 7, 3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("FR-03: nextReminderAt trả lần nhắc kế tiếp theo số lần đã gửi, rỗng khi đã gửi đủ")
    void nextReminderAt_followsRemindersSent() {
        DmsSchedule schedule = calculator.calculate(CYCLE_START, 30, 7, 3);
        List<LocalDateTime> reminders = schedule.reminderTimes();

        assertThat(schedule.nextReminderAt(0)).contains(reminders.get(0));
        assertThat(schedule.nextReminderAt(1)).contains(reminders.get(1));
        assertThat(schedule.nextReminderAt(2)).contains(reminders.get(2));
        assertThat(schedule.nextReminderAt(3)).isEmpty();
        assertThat(schedule.nextReminderAt(4)).isEmpty();
    }

    @Test
    @DisplayName("FR-03: nextReminderAt với số âm -> IllegalArgumentException")
    void nextReminderAt_rejectsNegative() {
        DmsSchedule schedule = calculator.calculate(CYCLE_START, 30, 7, 3);

        assertThatThrownBy(() -> schedule.nextReminderAt(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("FR-03: reminderTimes bất biến, không bị ảnh hưởng khi list gốc thay đổi")
    void schedule_reminderTimesAreImmutable() {
        LocalDateTime due = CYCLE_START.plusDays(30);
        List<LocalDateTime> source = new ArrayList<>(List.of(due));
        DmsSchedule schedule = new DmsSchedule(due, due.plusDays(7), source);

        source.add(due.plusDays(1));

        assertThat(schedule.reminderTimes()).containsExactly(due);
        assertThatThrownBy(() -> schedule.reminderTimes().add(due))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
