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
 * Không gửi mail ở đây: trả về nội dung cần gửi để DmsReminderService gửi sau khi transaction đã commit,
 * rồi gọi recordReminderSent (gửi được) hoặc releaseReminder (gửi lỗi).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DmsReminderProcessor {

    private final DmsConfigRepository dmsConfigRepository;
    private final DmsCheckinRepository dmsCheckinRepository;
    private final DmsScheduleCalculator scheduleCalculator = new DmsScheduleCalculator();

    /**
     * Thông tin mail nhắc cần gửi; reminderNumber tính từ 1.
     * previousRemindersSent: remindersSent trước lần nhắc này, để releaseReminder khôi phục đúng khi gửi lỗi
     * (lần nhắc có thể nhảy nhiều bậc, ví dụ 0 -> 2).
     */
    public record ReminderMail(String ownerEmail, LocalDateTime checkinDueAt, LocalDateTime triggerAt,
                               int reminderNumber, int reminderCount, int previousRemindersSent) {
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

        // Q27: dùng mốc trigger thật trong DB, không tính lại từ lastCheckinAt: FR-07 có thể đã hoãn kích hoạt
        // và lùi nextCheckinDueAt, khi đó vẫn phải gửi lần nhắc bù với hạn mới.
        LocalDateTime triggerAt = config.getNextCheckinDueAt();
        if (triggerAt == null || !now.isBefore(triggerAt)) {
            // Đã qua thời điểm kích hoạt (server tắt quá lâu): nhắc check-in không còn ý nghĩa, để FR-07 trigger.
            // KHÔNG đổi remindersSent: remindersSent == 0 nghĩa là chưa gửi thành công lần nhắc nào (FR-07 dựa vào đó để hoãn).
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

        int previousRemindersSent = config.getRemindersSent();
        config.setRemindersSent(due);
        config.setNextReminderAt(schedule.nextReminderAt(due).orElse(null));
        dmsConfigRepository.saveAndFlush(config);

        // DmsCheckin REMINDER_SENT chỉ ghi sau khi gửi mail thành công (recordReminderSent).
        return Optional.of(new ReminderMail(config.getOwner().getEmail(), schedule.checkinDueAt(),
                triggerAt, due, reminderCount, previousRemindersSent));
    }

    /** M1: ghi nhận lần nhắc đã gửi mail thành công, trong transaction riêng sau khi gửi. */
    @Transactional
    public void recordReminderSent(UUID configId, LocalDateTime now) {
        DmsConfig config = dmsConfigRepository.findById(configId).orElse(null);
        if (config == null) {
            return;
        }
        dmsCheckinRepository.save(DmsCheckin.builder()
                .dmsConfig(config)
                .checkinType(DmsCheckinType.REMINDER_SENT)
                .channel(NotificationChannel.EMAIL)
                .occurredAt(now)
                .build());
    }

    /**
     * M1: gửi mail lỗi thì trả lại lần nhắc để lượt quét kế tiếp gửi lại (gửi ít nhất một lần).
     * Chỉ hoàn tác khi chưa ai đổi trạng thái: Owner vừa check-in, DMS đã trigger hoặc lượt khác đã xử lý thì bỏ qua.
     * Khôi phục về previousRemindersSent (không phải reminderNumber - 1) để không tính các lần nhắc bị nhảy qua là đã gửi.
     */
    @Transactional
    public void releaseReminder(UUID configId, int reminderNumber, int previousRemindersSent, LocalDateTime now) {
        DmsConfig config = dmsConfigRepository.findById(configId).orElse(null);
        if (config == null || config.getStatus() != DmsStatus.ACTIVE
                || config.getRemindersSent() != reminderNumber) {
            return;
        }
        config.setRemindersSent(previousRemindersSent);
        config.setNextReminderAt(now);
        dmsConfigRepository.saveAndFlush(config);
    }
}
