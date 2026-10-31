package com.ltld.app.legacyvault.service.paymentservice;

import com.ltld.app.legacyvault.entity.Subscription;
import com.ltld.app.legacyvault.entity.User;
import com.ltld.app.legacyvault.enums.SubscriptionStatus;
import com.ltld.app.legacyvault.repository.SubscriptionRepository;
import com.ltld.app.legacyvault.utility.EmailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * T1: Quét hàng ngày để đánh dấu gói VIP hết hạn (EXPIRED) và gửi nhắc trước 7 ngày.
 * Khi hết hạn chỉ chặn tạo mới vượt hạn mức FREE, không xoá dữ liệu và không dừng DMS/bàn giao.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.subscription.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class SubscriptionExpiryScheduler {

    private final SubscriptionRepository subscriptionRepository;
    private final EmailSender emailSender;

    @Scheduled(cron = "${app.subscription.expiry-cron:0 0 8 * * *}")
    @Transactional
    public void runDailyCheck() {
        processExpirationsAndReminders(LocalDateTime.now());
    }

    @Transactional
    public int processExpirationsAndReminders(LocalDateTime now) {
        List<Subscription> expiredList = subscriptionRepository
                .findByStatusAndExpiresAtBefore(SubscriptionStatus.ACTIVE, now);
        for (Subscription sub : expiredList) {
            sub.setStatus(SubscriptionStatus.EXPIRED);
            subscriptionRepository.save(sub);
            log.info("Marked subscription {} as EXPIRED for user {}",
                    sub.getId(), sub.getUser() != null ? sub.getUser().getId() : null);
        }

        LocalDateTime sevenDaysLater = now.plusDays(7);
        List<Subscription> expiringSoon = subscriptionRepository
                .findByStatusAndReminderSentFalseAndExpiresAtBetween(
                        SubscriptionStatus.ACTIVE, now, sevenDaysLater);
        for (Subscription sub : expiringSoon) {
            User user = sub.getUser();
            if (user != null && user.getEmail() != null) {
                String planName = sub.getPlan() != null ? sub.getPlan().getName() : "VIP";
                emailSender.sendSubscriptionExpiryReminder(
                        user.getEmail(),
                        user.getFullName(),
                        planName,
                        sub.getExpiresAt()
                );
                sub.setReminderSent(true);
                subscriptionRepository.save(sub);
            }
        }
        return expiredList.size();
    }
}
