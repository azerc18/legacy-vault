package com.ltld.app.legacyvault.utility;

import com.ltld.app.legacyvault.enums.AppLanguage;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Chữ của email theo ngôn ngữ, đọc từ mail/mail_vi.properties và mail/mail_en.properties (UTF-8).
 * Khóa thiếu ở bản EN thì rơi về VI; thiếu cả hai thì trả lại chính tên khóa (không ném lỗi, không làm hỏng việc gửi).
 */
public final class MailMessages {

    private static final String BASE = "mail/mail";

    private MailMessages() {
    }

    public static String get(AppLanguage language, String key, Object... args) {
        String pattern = lookup(language, key);
        if (pattern == null && language != AppLanguage.VI) {
            pattern = lookup(AppLanguage.VI, key);
        }
        if (pattern == null) {
            return key;
        }
        return args == null || args.length == 0 ? pattern : new MessageFormat(pattern, Locale.ROOT).format(args);
    }

    public static boolean has(AppLanguage language, String key) {
        return lookup(language, key) != null;
    }

    private static String lookup(AppLanguage language, String key) {
        try {
            ResourceBundle bundle = ResourceBundle.getBundle(BASE, language.toLocale(),
                    ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
            return bundle.containsKey(key) ? bundle.getString(key) : null;
        } catch (MissingResourceException e) {
            return null;
        }
    }
}
