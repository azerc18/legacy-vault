package com.ltld.app.legacyvault.service.notificationservice;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.Temporal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tham số của một thông báo trong app, lưu ở cột notifications.params dạng JSON phẳng {"khóa": "giá trị"}.
 * FE dịch câu thông báo bằng khóa i18n notifications.types.TYPE và điền các tham số này; khóa kết thúc bằng
 * "At" là thời điểm ISO có múi giờ (kết thúc bằng Z), FE tự định dạng theo ngôn ngữ và múi giờ của trình duyệt.
 */
public final class NotificationParams {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<LinkedHashMap<String, String>> MAP = new TypeReference<>() {
    };

    private NotificationParams() {
    }

    /** of("owner", "An", "deadlineAt", LocalDateTime...): bỏ qua cặp có giá trị null. */
    public static Map<String, String> of(Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("keyValues must be key/value pairs");
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            Object value = keyValues[i + 1];
            if (value != null) {
                map.put(String.valueOf(keyValues[i]), text(value));
            }
        }
        return map;
    }

    // LocalDateTime trong app là giờ của JVM (server có thể chạy UTC): đổi sang Instant để chuỗi mang múi giờ,
    // nếu không trình duyệt sẽ hiểu là giờ địa phương và lệch so với giờ trong email. Temporal khác (LocalDate,
    // Instant) có toString là ISO sẵn.
    private static String text(Object value) {
        if (value instanceof LocalDateTime dateTime) {
            return toInstantText(dateTime);
        }
        return value instanceof Temporal ? value.toString() : String.valueOf(value);
    }

    /** Thời điểm ISO có múi giờ (…Z) của một LocalDateTime theo giờ JVM. */
    public static String toInstantText(LocalDateTime dateTime) {
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().toString();
    }

    /** Dùng ở builder của Notification: .params(NotificationParams.json("vault", name, ...)). */
    public static String json(Object... keyValues) {
        return toJson(of(keyValues));
    }

    public static String toJson(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return null;
        }
        try {
            return JSON.writeValueAsString(params);
        } catch (JacksonException e) {
            return null;
        }
    }

    /** JSON hỏng hoặc rỗng thì trả map rỗng để FE rơi về content. */
    public static Map<String, String> parse(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return JSON.readValue(json, MAP);
        } catch (JacksonException e) {
            return Map.of();
        }
    }
}
