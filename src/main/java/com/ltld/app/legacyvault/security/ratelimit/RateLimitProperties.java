package com.ltld.app.legacyvault.security.ratelimit;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Giới hạn số request theo IP cho các API công khai (chưa đăng nhập) dưới /api/auth.
 * Giới hạn theo tài khoản (OTP 60 giây, khoá đăng nhập 15 phút) không chặn được kẻ dùng nhiều email khác nhau,
 * nên thêm lớp này. Mỗi quy tắc có một "xô" riêng cho mỗi IP; một IP hết lượt ở quy tắc này vẫn gọi được quy tắc khác.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    /** Tắt thì filter bỏ qua mọi request (test tắt sẵn trong src/test/resources). */
    private boolean enabled = true;

    /**
     * true: lấy IP người dùng từ X-Forwarded-For (giá trị cuối, do proxy gần nhất thêm vào).
     * Chỉ bật khi app chạy sau một proxy tin cậy luôn ghi header này (Azure App Service, profile prod).
     * Bật khi không có proxy thì kẻ xấu tự đặt header để đổi IP và lách giới hạn.
     */
    private boolean trustForwardedFor = false;

    /** Số cặp (quy tắc, IP) tối đa giữ trong bộ nhớ trước khi dọn các xô đã đầy lại. */
    private int maxTrackedKeys = 50_000;

    /** Tên quy tắc -> quy tắc. Chỉ áp dụng cho POST. */
    private Map<String, Rule> rules = new LinkedHashMap<>();

    @Getter
    @Setter
    public static class Rule {
        /** Đường dẫn chính xác, ví dụ /api/auth/register. */
        private List<String> paths = new ArrayList<>();
        /** Số request tối đa trong một chu kỳ. */
        private int limit;
        /** Độ dài chu kỳ; lượt được hồi dần đều trong chu kỳ (token bucket). */
        private Duration period;
    }
}
