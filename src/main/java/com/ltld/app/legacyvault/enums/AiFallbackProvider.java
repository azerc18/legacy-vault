package com.ltld.app.legacyvault.enums;

/**
 * Nhà cung cấp AI dự phòng khi Gemini lỗi. Cả hai dùng API chuẩn OpenAI (/chat/completions).
 * DeepSeek chỉ đọc văn bản; Groq đọc được cả ảnh (model vision).
 */
public enum AiFallbackProvider {
    GROQ, DEEPSEEK;

    /** Chuỗi cấu hình (không phân biệt hoa thường); sai hoặc trống thì null. */
    public static AiFallbackProvider parse(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
