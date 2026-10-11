package com.ltld.app.legacyvault.service.dmsservice;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;

/**
 * Chạy FR-07 định kỳ. Mặc định BẬT; tắt bằng app.digital-executor.scheduler.enabled=false
 * (profile dev tắt để tự điều khiển thời gian từ test console).
 */
@Configuration
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.digital-executor.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class DmsAutomationScheduler {

    private final DmsAutomationService dmsAutomationService;

    // Quét DMS quá hạn check-in (FR-07 bước 1-3).
    @Scheduled(fixedDelayString = "${app.digital-executor.dms-trigger-scan-delay-ms:60000}")
    public void processOverdueDms() {
        dmsAutomationService.processOverdueDms(LocalDateTime.now());
    }

    // Leo thang Backup sau thời gian chờ cấu hình (BR-03, mặc định 14 ngày) và gửi lại thông báo lỗi.
    // Chạy ít thường xuyên hơn trigger vì chỉ cần kiểm tra sau N ngày chờ.
    @Scheduled(fixedDelayString = "${app.digital-executor.dms-escalate-scan-delay-ms:300000}")
    public void activateBackupAfterTimeout() {
        dmsAutomationService.activateBackupForUnacknowledgedPrimary(LocalDateTime.now());
    }
}
