package com.ltld.app.legacyvault.service.dmsservice;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * FR-03: lịch của một chu kỳ DMS — hạn check-in, các lần nhắc trong thời gian chờ và thời điểm kích hoạt.
 * reminderTimes được sao chép thành list bất biến, sắp theo thời gian tăng dần.
 */
public record DmsSchedule(LocalDateTime checkinDueAt, LocalDateTime triggerAt, List<LocalDateTime> reminderTimes) {

    public DmsSchedule {
        reminderTimes = List.copyOf(reminderTimes);
    }

    /** Lần nhắc kế tiếp khi đã gửi remindersSent lần; rỗng nếu đã gửi đủ. */
    public Optional<LocalDateTime> nextReminderAt(int remindersSent) {
        if (remindersSent < 0) {
            throw new IllegalArgumentException("remindersSent must not be negative: " + remindersSent);
        }
        return remindersSent < reminderTimes.size()
                ? Optional.of(reminderTimes.get(remindersSent))
                : Optional.empty();
    }
}
