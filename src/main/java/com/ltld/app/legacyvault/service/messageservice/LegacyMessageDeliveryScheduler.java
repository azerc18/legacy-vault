package com.ltld.app.legacyvault.service.messageservice;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * F4b: Scheduler quét định kỳ các lời nhắn hẹn giờ tương lai (Time-Capsule: SPECIFIC_DATE có deliver_date <= hôm nay)
 * và các lời nhắn ON_CLAIM chưa gửi trong các Vault đã mở khóa (UNLOCKED / CLAIMED) để gửi thông báo cho người thụ hưởng.
 * Mặc định BẬT; có thể tắt bằng app.legacy-message.scheduler.enabled=false trong test.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.legacy-message.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class LegacyMessageDeliveryScheduler {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final LegacyMessageService legacyMessageService;

    @Scheduled(fixedDelayString = "${app.legacy-message.delivery-scan-delay:PT1H}")
    public void scanAndDeliverDueMessages() {
        deliverDueMessages(LocalDate.now(VN_ZONE));
    }

    public int deliverDueMessages(LocalDate today) {
        try {
            int count = legacyMessageService.deliverDueScheduledMessages(today);
            if (count > 0) {
                log.info("F4b: scheduler delivered {} due legacy message(s) for date {}", count, today);
            }
            return count;
        } catch (RuntimeException e) {
            log.error("F4b: scheduler failed to deliver due legacy messages for date {}: {}", today, e.getMessage(), e);
            return 0;
        }
    }
}
