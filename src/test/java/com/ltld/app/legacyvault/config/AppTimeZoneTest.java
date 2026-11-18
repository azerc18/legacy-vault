package com.ltld.app.legacyvault.config;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class AppTimeZoneTest {

    @Test
    void resolve_defaultsToVietnam() {
        assertThat(AppTimeZone.resolve(null)).isEqualTo(ZoneId.of("Asia/Ho_Chi_Minh"));
        assertThat(AppTimeZone.resolve("  ")).isEqualTo(AppTimeZone.VN);
    }

    @Test
    void resolve_usesConfiguredZone() {
        assertThat(AppTimeZone.resolve("UTC")).isEqualTo(ZoneId.of("UTC"));
        assertThat(AppTimeZone.resolve(" Asia/Tokyo ")).isEqualTo(ZoneId.of("Asia/Tokyo"));
    }

    @Test
    void resolve_fallsBackToVietnamOnInvalidZone() {
        assertThat(AppTimeZone.resolve("Mars/Olympus")).isEqualTo(AppTimeZone.VN);
    }
}
