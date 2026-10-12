package com.ltld.app.legacyvault.service.dmsservice;

import com.ltld.app.legacyvault.entity.DmsCheckin;
import com.ltld.app.legacyvault.entity.DmsConfig;
import com.ltld.app.legacyvault.enums.DmsCheckinType;
import com.ltld.app.legacyvault.enums.DmsStatus;
import com.ltld.app.legacyvault.enums.NotificationChannel;
import com.ltld.app.legacyvault.repository.DmsCheckinRepository;
import com.ltld.app.legacyvault.repository.DmsConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * FR-03: ghi nhận MỘT lần nhắc check-in cho MỘT Owner trong một transaction riêng.
 * Không gửi mail ở đây: trả về nội dung cần gửi để DmsReminderService gửi sau khi transaction đã commit.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DmsReminderProcessor {

    private final DmsConfigRepository dmsConfigRepository;
    private final DmsCheckinRepository dmsCheckinRepository;
    private final DmsScheduleCalculator scheduleCalculator = new DmsScheduleCalculator();

    /** Thông tin mail nhắc cần gửi; reminderNumber tính từ 1. */
    public record ReminderMail(String ownerEmail, LocalDateTime checkinDueAt, LocalDateTime triggerAt,
                               int reminderNumber, int reminderCount) {
    }

    @Transactional
    public Optional<ReminderMail> prepareReminder(UUID configId, LocalDateTime now) {
        DmsConfig config = dmsConfigRepository.findById(configId).orElse(null);
        // Kiểm tra lại trong transaction: Owner có thể vừa check-in hoặc DMS vừa bị kích hoạt.
        if (config == null || config.getStatus() != DmsStatus.ACTIVE
                || config.getNextReminderAt() == null || config.getNextReminderAt().isAfter(now)) {
            return Optional.empty();
        }
        if (config.getLastCheckinAt() == null || config.getCheckinIntervalDays() == null
                || config.getGracePeriodDays() == null || config.getReminderCount() == null) {
            log.warn("FR-03: dmsConfig {} has nextReminderAt but incomplete schedule settings", configId);
            return Optional.empty();
        }

        DmsSchedule schedule = scheduleCalculator.calculate(config.getLastCheckinAt(),
                config.getCheckinIntervalDays(), config.getGracePeriodDays(), config.getReminderCount());
        int reminderCount = schedule.reminderTimes().size();
        // Số lần nhắc đã đến hạn tính tới now. Server tắt lâu thì có thể > remindersSent + 1:
        // nhảy thẳng tới lần mới nhất và chỉ gửi một mail.
        int due = (int) schedule.reminderTimes().stream().filter(t -> !t.isAfter(now)).count();

        if (!now.isBefore(schedule.triggerAt())) {
            // Đã qua thời điểm kích hoạt (server tắt quá lâu): nhắc check-in không còn ý nghĩa, để FR-07 trigger.
            config.setRemindersSent(reminderCount);
            config.setNextReminderAt(null);
            dmsConfigRepository.saveAndFlush(config);
            return Optional.empty();
        }
        if (due <= config.getRemindersSent()) {
            // nextReminderAt lệch với lịch (không có lần nhắc mới): sửa lại mốc, không gửi.
            config.setNextReminderAt(schedule.nextReminderAt(config.getRemindersSent()).orElse(null));
            dmsConfigRepository.saveAndFlush(config);
            return Optional.empty();
        }

        config.setRemindersSent(due);
        config.setNextReminderAt(schedule.nextReminderAt(due).orElse(null));
        dmsConfigRepository.saveAndFlush(config);
        dmsCheckinRepository.save(DmsCheckin.builder()
                .dmsConfig(config)
                .checkinType(DmsCheckinType.REMINDER_SENT)
                .channel(NotificationChannel.EMAIL)
                .occurredAt(now)
                .build());

        return Optional.of(new ReminderMail(config.getOwner().getEmail(), schedule.checkinDueAt(),
                schedule.triggerAt(), due, reminderCount));
    }
}
