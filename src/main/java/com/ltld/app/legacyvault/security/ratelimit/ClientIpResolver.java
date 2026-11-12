package com.ltld.app.legacyvault.security.ratelimit;

import jakarta.servlet.http.HttpServletRequest;

import java.util.regex.Pattern;

/**
 * Lấy IP người dùng để giới hạn request.
 * Trên Azure App Service, app đứng sau proxy nên {@code getRemoteAddr()} là IP của proxy (mọi người chung một IP).
 * Khi {@code trustForwardedFor} bật: lấy giá trị CUỐI của X-Forwarded-For, là giá trị proxy tin cậy tự thêm vào.
 * Không lấy giá trị đầu, vì giá trị đầu do trình duyệt gửi lên và ai cũng tự đặt được.
 * Azure ghi kèm cổng ("1.2.3.4:51234"), nên bỏ phần cổng, nếu không mỗi kết nối sẽ thành một "IP" khác.
 */
public class ClientIpResolver {

    static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final Pattern IP_CHARS = Pattern.compile("[0-9A-Fa-f.:]{2,45}");

    private final boolean trustForwardedFor;

    public ClientIpResolver(boolean trustForwardedFor) {
        this.trustForwardedFor = trustForwardedFor;
    }

    public String resolve(HttpServletRequest request) {
        if (trustForwardedFor) {
            String fromHeader = lastForwardedFor(request.getHeader(X_FORWARDED_FOR));
            if (fromHeader != null) return fromHeader;
        }
        return request.getRemoteAddr();
    }

    static String lastForwardedFor(String header) {
        if (header == null || header.isBlank()) return null;
        String[] parts = header.split(",");
        String last = parts[parts.length - 1].trim();
        String ip = stripPort(last);
        return ip != null && IP_CHARS.matcher(ip).matches() ? ip : null;
    }

    private static String stripPort(String value) {
        if (value.isEmpty()) return null;
        if (value.startsWith("[")) {                 // IPv6 có cổng: [2001:db8::1]:443
            int end = value.indexOf(']');
            return end > 1 ? value.substring(1, end) : null;
        }
        int colon = value.indexOf(':');
        if (colon > 0 && colon == value.lastIndexOf(':')) {   // IPv4 có cổng: 1.2.3.4:51234
            return value.substring(0, colon);
        }
        return value;                                 // IPv4 hoặc IPv6 không cổng
    }
}
