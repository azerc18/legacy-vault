package com.ltld.app.legacyvault.service.auditservice;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** FR-20: quét audit log định kỳ. Mặc định BẬT; tắt bằng app.security-alerts.enabled=false. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.security-alerts.enabled", havingValue = "true", matchIfMissing = true)
public class SecurityAlertScheduler {

    private final SecurityAlertDetector detector;

    @Scheduled(fixedDelayString = "${app.security-alerts.scan-delay:PT5M}",
            initialDelayString = "${app.security-alerts.initial-delay:PT1M}")
    public void scan() {
        try {
            int created = detector.detect(Instant.now());
            if (created > 0) log.info("Security alert scan created {} alert(s)", created);
        } catch (RuntimeException e) {
            log.error("Security alert scan failed", e);   // không để 1 lần lỗi làm dừng job
        }
    }
}