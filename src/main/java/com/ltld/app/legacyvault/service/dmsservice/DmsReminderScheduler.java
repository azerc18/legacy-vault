package com.ltld.app.legacyvault.service.dmsservice;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * FR-03: quét định kỳ và gửi nhắc check-in DMS. Dùng chung công tắc app.dms.scheduler.enabled với FR-07;
 * scheduling được bật ở DmsSchedulingConfig.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.dms.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class DmsReminderScheduler {

    private final DmsReminderService dmsReminderService;

    @Scheduled(fixedDelayString = "${app.dms.reminder-scan-delay:PT15M}")
    public void sendDueReminders() {
        dmsReminderService.sendDueReminders(LocalDateTime.now());
    }
}
