package com.ltld.app.legacyvault.service.dmsservice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DmsAutomationScheduler – chu kỳ quét mặc định")
class DmsAutomationSchedulerDelayTest {

    private static String defaultOf(String placeholder) {
        // "${key:DEFAULT}" -> "DEFAULT"
        return placeholder.substring(placeholder.indexOf(':') + 1, placeholder.length() - 1);
    }

    @Test
    @DisplayName("quét DMS quá hạn: mặc định 20 giây, vẫn đổi được bằng app.dms.trigger-scan-delay")
    void triggerScan_defaultsTo20Seconds() throws Exception {
        Scheduled s = DmsAutomationScheduler.class.getMethod("processOverdueDms").getAnnotation(Scheduled.class);

        assertThat(s.fixedDelayString()).startsWith("${app.dms.trigger-scan-delay:");
        assertThat(Duration.parse(defaultOf(s.fixedDelayString()))).isEqualTo(Duration.ofSeconds(20));
    }

    @Test
    @DisplayName("leo thang Backup: giữ 5 phút (không đổi theo chu kỳ trigger)")
    void escalateScan_unchanged() throws Exception {
        Scheduled s = DmsAutomationScheduler.class.getMethod("escalateUnresponsivePrimaries").getAnnotation(Scheduled.class);

        assertThat(Duration.parse(defaultOf(s.fixedDelayString()))).isEqualTo(Duration.ofMinutes(5));
    }
}
