package com.ltld.app.legacyvault.service.paymentservice;

import com.ltld.app.legacyvault.entity.Subscription;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.SubscriptionStatus;
import com.ltld.app.legacyvault.repository.SubscriptionRepository;
import com.ltld.app.legacyvault.utility.EmailSender;
import com.ltld.app.legacyvault.service.notificationservice.InAppNotifier;
import com.ltld.app.legacyvault.enums.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * T1: Quét hàng ngày để đánh dấu gói VIP hết hạn (EXPIRED) và gửi nhắc trước 7 ngày.
 * Khi hết hạn chỉ chặn tạo mới vượt hạn mức FREE, không xoá dữ liệu và không dừng DMS/bàn giao.
 *
 * <p>Đổi EXPIRED và đánh dấu reminderSent diễn ra trong MỘT transaction; mail chỉ gửi SAU khi transaction commit,
 * mỗi mail một try/catch riêng: gửi lỗi thì log và đặt lại reminderSent=false để lần quét sau thử lại.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.subscription.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class SubscriptionExpiryScheduler {

    private final SubscriptionRepository subscriptionRepository;
    private final EmailSender emailSender;
    private final InAppNotifier inAppNotifier;
    private final TransactionOperations transactionOperations;

    @Scheduled(cron = "${app.subscription.expiry-cron:0 0 8 * * *}")
    public void runDailyCheck() {
        processExpirationsAndReminders(LocalDateTime.now());
    }

    /** @return số gói vừa được đánh dấu EXPIRED */
    public int processExpirationsAndReminders(LocalDateTime now) {
        Batch batch = transactionOperations.execute(status -> markExpiredAndSelectReminders(now));
        if (batch == null) {
            return 0;
        }
        for (Reminder reminder : batch.reminders()) {
            sendReminder(reminder);
        }
        for (Reminder expired : batch.expired()) {
            sendExpiredNotice(expired);
        }
        return batch.expiredCount();
    }

    private Batch markExpiredAndSelectReminders(LocalDateTime now) {
        List<Subscription> expiredList = subscriptionRepository
                .findByStatusAndExpiresAtBefore(SubscriptionStatus.ACTIVE, now);
        List<Reminder> expiredNotices = new ArrayList<>();
        for (Subscription sub : expiredList) {
            sub.setStatus(SubscriptionStatus.EXPIRED);
            subscriptionRepository.save(sub);
            log.info("Marked subscription {} as EXPIRED for user {}",
                    sub.getId(), sub.getUser() != null ? sub.getUser().getId() : null);
            if (sub.getUser() != null && sub.getUser().getEmail() != null) {
                expiredNotices.add(new Reminder(sub.getId(), sub.getUser().getEmail(), sub.getUser().getFullName(),
                        sub.getPlan() != null ? sub.getPlan().getName() : "VIP", sub.getExpiresAt()));
            }
        }

        List<Reminder> reminders = new ArrayList<>();
        List<Subscription> expiringSoon = subscriptionRepository
                .findByStatusAndReminderSentFalseAndExpiresAtBetween(
                        SubscriptionStatus.ACTIVE, now, now.plusDays(7));
        for (Subscription sub : expiringSoon) {
            User user = sub.getUser();
            if (user == null || user.getEmail() == null) {
                continue;
            }
            sub.setReminderSent(true);
            subscriptionRepository.save(sub);
            reminders.add(new Reminder(
                    sub.getId(),
                    user.getEmail(),
                    user.getFullName(),
                    sub.getPlan() != null ? sub.getPlan().getName() : "VIP",
                    sub.getExpiresAt()));
        }
        return new Batch(expiredList.size(), reminders, expiredNotices);
    }

    // N12: EXPIRED đã commit nên gửi lỗi không thử lại được; chỉ log, gói vẫn hết hạn đúng
    private void sendExpiredNotice(Reminder notice) {
        inAppNotifier.toEmail(notice.email(), InAppNotifier.draft(NotificationType.SUBSCRIPTION_EXPIRED, null, null,
                "Your " + notice.planName() + " plan has expired.", "plan", notice.planName()));
        try {
            emailSender.sendSubscriptionExpiredNotice(notice.email(), notice.fullName(), notice.planName());
        } catch (RuntimeException ex) {
            log.warn("N12: could not send expired notice for subscription {}: {}", notice.subscriptionId(), ex.getMessage());
        }
    }

    private void sendReminder(Reminder reminder) {
        // toEmailOnce: SMTP lỗi thì reminderSent được trả lại và lượt sau chạy lại hàm này
        inAppNotifier.toEmailOnce(reminder.email(), InAppNotifier.draft(NotificationType.SUBSCRIPTION_EXPIRING, null, null,
                "Your " + reminder.planName() + " plan is about to expire.",
                "plan", reminder.planName(), "expiresAt", reminder.expiresAt()));
        try {
            emailSender.sendSubscriptionExpiryReminder(
                    reminder.email(), reminder.fullName(), reminder.planName(), reminder.expiresAt());
        } catch (RuntimeException ex) {
            log.warn("T1: could not send expiry reminder for subscription {}: {}", reminder.subscriptionId(), ex.getMessage());
            resetReminderSent(reminder.subscriptionId());
        }
    }

    private void resetReminderSent(UUID subscriptionId) {
        try {
            transactionOperations.executeWithoutResult(status ->
                    subscriptionRepository.findById(subscriptionId).ifPresent(sub -> {
                        sub.setReminderSent(false);
                        subscriptionRepository.save(sub);
                    }));
        } catch (RuntimeException ex) {
            log.warn("T1: could not reset reminderSent for subscription {}: {}", subscriptionId, ex.getMessage());
        }
    }

    private record Batch(int expiredCount, List<Reminder> reminders, List<Reminder> expired) {
    }

    private record Reminder(UUID subscriptionId, String email, String fullName, String planName, LocalDateTime expiresAt) {
    }
}
