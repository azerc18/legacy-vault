package com.ltld.app.legacyvault.enums;

import java.util.Locale;

/**
 * Ngôn ngữ người dùng chọn để nhận email và nội dung hệ thống gửi cho họ (hoá đơn luôn song ngữ nên không phụ thuộc).
 * Lưu theo tên enum (VI / EN) trong cột users.preferred_language; FE và API dùng mã ngắn "vi" / "en".
 */
public enum AppLanguage {
    VI("vi"),
    EN("en");

    /** Ngôn ngữ mặc định khi chưa có lựa chọn (người dùng cũ, người nhận chưa có tài khoản). */
    public static final AppLanguage DEFAULT = VI;

    private final String tag;

    AppLanguage(String tag) {
        this.tag = tag;
    }

    /** Mã ngắn dùng trong API: "vi" hoặc "en". */
    public String tag() {
        return tag;
    }

    public Locale toLocale() {
        return Locale.forLanguageTag(tag);
    }

    /**
     * Đọc từ mã ngôn ngữ do client gửi ("vi", "en", "vi-VN", "en_US"...). Rỗng hoặc không hỗ trợ thì trả về
     * {@link #DEFAULT}, để một giá trị lạ không làm hỏng đăng ký.
     */
    public static AppLanguage fromTag(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT;
        }
        String lang = raw.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        int dash = lang.indexOf('-');
        if (dash > 0) {
            lang = lang.substring(0, dash);
        }
        for (AppLanguage l : values()) {
            if (l.tag.equals(lang)) {
                return l;
            }
        }
        return DEFAULT;
    }
}
