package com.ltld.app.legacyvault.service.beneficiaryservice;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * FR-16: gửi lại thông báo bàn giao cho Beneficiary khi lần gửi trước thất bại.
 * Mặc định BẬT; tắt bằng app.beneficiary.notify-retry.enabled=false (dùng trong integration test).
 * Scheduling được bật ở DmsSchedulingConfig.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.beneficiary.notify-retry.enabled", havingValue = "true", matchIfMissing = true)
public class BeneficiaryNotificationRetryScheduler {

    private final BeneficiaryNotificationListener listener;

    @Scheduled(fixedDelayString = "${app.beneficiary.notify-retry-delay:PT1H}")
    public void retryPendingNotifications() {
        listener.retryPendingNotifications(LocalDateTime.now());
    }
}