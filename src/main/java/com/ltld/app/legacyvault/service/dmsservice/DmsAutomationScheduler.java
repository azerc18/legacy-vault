package com.ltld.app.legacyvault.service.dmsservice;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Chạy FR-07 định kỳ. Mặc định BẬT; tắt bằng app.dms.scheduler.enabled=false.
 * Scheduling được bật ở DmsSchedulingConfig.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.dms.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class DmsAutomationScheduler {

    private final DmsAutomationService dmsAutomationService;

    // Quét DMS quá hạn check-in (FR-07 bước 1-3). Mặc định 20 giây (trước là 1 phút) để Owner quá hạn
    // được phát hiện gần như ngay, kể cả khi demo tua next_checkin_due_at bằng SQL. Đổi bằng app.dms.trigger-scan-delay.
    @Scheduled(fixedDelayString = "${app.dms.trigger-scan-delay:PT20S}")
    public void processOverdueDms() {
        dmsAutomationService.processOverdueDms(LocalDateTime.now());
    }

    // Leo thang Backup sau thời gian chờ cấu hình (BR-03, mặc định 14 ngày) và gửi lại thông báo lỗi.
    // Chạy ít thường xuyên hơn trigger vì chỉ cần kiểm tra sau N ngày chờ.
    @Scheduled(fixedDelayString = "${app.dms.escalate-scan-delay:PT5M}")
    public void escalateUnresponsivePrimaries() {
        dmsAutomationService.escalateUnresponsivePrimaries(LocalDateTime.now());
    }
}
