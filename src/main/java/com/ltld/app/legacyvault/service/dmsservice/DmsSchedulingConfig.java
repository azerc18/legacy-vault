package com.ltld.app.legacyvault.service.dmsservice;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Bật @Scheduled cho toàn ứng dụng (FR-07 cần cho DmsAutomationScheduler).
 * Tách khỏi DmsAutomationScheduler để tắt app.dms.scheduler.enabled không tắt scheduling của module khác.
 */
@Configuration
@EnableScheduling
public class DmsSchedulingConfig {
}
