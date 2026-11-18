package com.ltld.app.legacyvault.config;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

class AppTimeZoneTest {

    @Test
    void applyDefault_setsJvmZoneToVietnam() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            AppTimeZone.applyDefault();
            assertThat(ZoneId.systemDefault()).isEqualTo(ZoneId.of("Asia/Ho_Chi_Minh"));
        } finally {
            TimeZone.setDefault(original);
        }
    }
}
