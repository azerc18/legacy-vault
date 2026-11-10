package com.ltld.app.legacyvault.service.notificationservice;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationParamsTest {

    @Test
    void json_roundTrip_keepsUnicodeQuotesAndSkipsNulls() {
        String json = NotificationParams.json("vault", "Két \"gia đình\"", "owner", null, "count", 2,
                "deadlineAt", LocalDateTime.of(2026, 11, 20, 9, 30));

        Map<String, String> parsed = NotificationParams.parse(json);

        assertThat(parsed).containsExactly(
                Map.entry("vault", "Két \"gia đình\""),
                Map.entry("count", "2"),
                Map.entry("deadlineAt", "2026-11-20T09:30"));
    }

    @Test
    void json_noValues_isNull_soOldRowsAndEmptyParamsLookTheSame() {
        assertThat(NotificationParams.json()).isNull();
        assertThat(NotificationParams.json("reason", null)).isNull();
    }

    @Test
    void parse_nullBlankOrBrokenJson_returnsEmptyMap() {
        assertThat(NotificationParams.parse(null)).isEmpty();
        assertThat(NotificationParams.parse("  ")).isEmpty();
        assertThat(NotificationParams.parse("{not json")).isEmpty();
    }

    @Test
    void of_oddNumberOfArguments_isRejected() {
        assertThatThrownBy(() -> NotificationParams.of("vault")).isInstanceOf(IllegalArgumentException.class);
    }
}
