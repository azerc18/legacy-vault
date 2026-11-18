package com.ltld.app.legacyvault.config;

import java.time.ZoneId;
import java.util.TimeZone;

/**
 * Múi giờ của ứng dụng: cố định giờ Việt Nam (Asia/Ho_Chi_Minh, UTC+7).
 *
 * <p>Code dùng {@code LocalDateTime.now()} / {@code LocalDate.now()} theo múi giờ mặc định của JVM, và các
 * {@code LocalDateTime} trả về API không kèm offset. Máy chủ Azure chạy UTC nên trước đây màn hình hiện lệch 7 tiếng
 * (vd. check-in lúc 16:50 hiện 09:50). Vì vậy đặt múi giờ JVM là giờ VN ngay khi khởi động, trước Spring/Hibernate.
 *
 * <p>Cố ý KHÔNG cho đổi qua biến môi trường: FE ({@code frontend/src/i18n/format.js}, {@code parseServerDate}) hiểu
 * mọi {@code LocalDateTime} không offset từ BE là {@code +07:00}. Muốn đổi múi giờ thì phải đổi cả hai nơi.
 *
 * <p>Dữ liệu trong DB vẫn lưu theo UTC: {@code spring.jpa.properties.hibernate.jdbc.time_zone=UTC} cùng
 * {@code serverTimezone=UTC} trong URL JDBC quy đổi giờ VN sang UTC khi ghi và ngược lại khi đọc, nên dữ liệu cũ
 * (vốn là UTC) không phải migrate.
 */
public final class AppTimeZone {

    public static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private AppTimeZone() {
    }

    /** Đặt múi giờ mặc định của JVM là giờ VN; gọi đầu tiên trong {@code main}. */
    public static void applyDefault() {
        TimeZone.setDefault(TimeZone.getTimeZone(VN));
    }
}
