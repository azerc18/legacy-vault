package com.ltld.app.legacyvault.service.dmsservice;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * FR-03: tính lịch một chu kỳ DMS. Lớp thuần, không truy cập DB.
 * Các lần nhắc chia đều thời gian chờ, lần đầu đúng lúc đến hạn check-in, lần cuối luôn trước triggerAt.
 */
public class DmsScheduleCalculator {

    public DmsSchedule calculate(LocalDateTime cycleStart, int intervalDays, int gracePeriodDays, int reminderCount) {
        if (cycleStart == null) {
            throw new IllegalArgumentException("cycleStart must not be null");
        }
        requirePositive("intervalDays", intervalDays);
        requirePositive("gracePeriodDays", gracePeriodDays);
        requirePositive("reminderCount", reminderCount);

        LocalDateTime checkinDueAt = cycleStart.plusDays(intervalDays);
        LocalDateTime triggerAt = checkinDueAt.plusDays(gracePeriodDays);

        Duration step = Duration.ofDays(gracePeriodDays).dividedBy(reminderCount);
        List<LocalDateTime> reminderTimes = new ArrayList<>(reminderCount);
        for (int i = 0; i < reminderCount; i++) {
            reminderTimes.add(checkinDueAt.plus(step.multipliedBy(i)));
        }
        return new DmsSchedule(checkinDueAt, triggerAt, reminderTimes);
    }

    private static void requirePositive(String name, int value) {
        if (value < 1) {
            throw new IllegalArgumentException(name + " must be at least 1: " + value);
        }
    }
}
