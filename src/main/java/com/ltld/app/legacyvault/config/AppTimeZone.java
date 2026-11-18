package com.ltld.app.legacyvault.config;

import java.time.ZoneId;
import java.util.TimeZone;

/**
 * Múi giờ của ứng dụng: mặc định giờ Việt Nam (Asia/Ho_Chi_Minh, UTC+7).
 *
 * <p>Code dùng {@code LocalDateTime.now()} / {@code LocalDate.now()} theo múi giờ mặc định của JVM, và các
 * {@code LocalDateTime} trả về API không kèm offset nên FE hiểu là giờ VN. Máy chủ Azure chạy UTC nên trước đây
 * màn hình hiện lệch 7 tiếng (vd. check-in lúc 16:50 hiện 09:50). Vì vậy đặt múi giờ JVM là giờ VN ngay khi khởi
 * động, trước Spring/Hibernate.
 *
 * <p>Dữ liệu trong DB vẫn lưu theo UTC: {@code spring.jpa.properties.hibernate.jdbc.time_zone=UTC} (application.yaml)
 * quy đổi giờ VN sang UTC khi ghi và ngược lại khi đọc, nên dữ liệu cũ (vốn là UTC) không phải migrate.
 *
 * <p>Đổi múi giờ (hiếm khi cần) bằng biến môi trường {@code APP_TIME_ZONE}, vd. {@code APP_TIME_ZONE=UTC}.
 */
public final class AppTimeZone {

    public static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private AppTimeZone() {
    }

    /** Múi giờ sẽ dùng: {@code APP_TIME_ZONE} nếu hợp lệ, ngược lại giờ VN. */
    static ZoneId resolve(String configured) {
        if (configured == null || configured.isBlank()) {
            return VN;
        }
        try {
            return ZoneId.of(configured.trim());
        } catch (RuntimeException e) {
            System.err.println("APP_TIME_ZONE='" + configured + "' không hợp lệ, dùng " + VN);
            return VN;
        }
    }

    /** Đặt múi giờ mặc định của JVM; gọi đầu tiên trong {@code main}. */
    public static ZoneId applyDefault() {
        ZoneId zone = resolve(System.getenv("APP_TIME_ZONE"));
        TimeZone.setDefault(TimeZone.getTimeZone(zone));
        return zone;
    }
}
