package com.ltld.app.legacyvault.utility.mail;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Chữ của email, lấy từ resources/mail/mail.properties (tiếng Việt, mặc định) và mail_en.properties (tiếng Anh).
 * Khóa thiếu ở bản Anh tự rơi về bản Việt (file gốc), thiếu cả hai thì trả về chính khóa, không ném lỗi.
 * Tham số dạng {0}, {1}... được thay trực tiếp (không dùng MessageFormat nên dấu ' trong "Dead Man's Switch" không bị nuốt).
 */
public final class MailText {

    public static final Locale VI = Locale.forLanguageTag("vi");
    public static final Locale EN = Locale.ENGLISH;
    private static final String BUNDLE = "mail/mail";
    // Không rơi về locale mặc định của JVM (máy chủ en_US sẽ làm mail tiếng Việt thành tiếng Anh)
    private static final ResourceBundle.Control NO_FALLBACK =
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);

    private final Locale locale;

    public MailText(Locale locale) {
        this.locale = locale == null ? VI : locale;
    }

    public Locale locale() {
        return locale;
    }

    public String raw(String key) {
        try {
            return ResourceBundle.getBundle(BUNDLE, locale, NO_FALLBACK).getString(key);
        } catch (MissingResourceException e) {
            return key;
        }
    }

    /** Chuỗi thuần (dùng cho tiêu đề, nhãn nút...). */
    public String plain(String key, Object... args) {
        return fill(raw(key), args, false);
    }

    /** Một đoạn văn: bản text thường + bản HTML (tham số được tô đậm, mọi thứ đều escape). */
    public Txt txt(String key, Object... args) {
        String template = raw(key);
        return new Txt(fill(template, args, false), fill(template, args, true));
    }

    static String fill(String template, Object[] args, boolean html) {
        String out = html ? MailLayout.esc(template) : template;
        if (args == null) return out;
        for (int i = 0; i < args.length; i++) {
            String value = args[i] == null ? "" : String.valueOf(args[i]);
            String rep = html ? MailLayout.highlight(value) : value;
            out = out.replace("{" + i + "}", rep);
        }
        return out;
    }

    /** Văn bản hai dạng: plain cho phần text/plain, html cho phần text/html. */
    public record Txt(String plain, String html) {
        public static Txt of(String s) {
            return new Txt(s, MailLayout.esc(s));
        }
    }
}
