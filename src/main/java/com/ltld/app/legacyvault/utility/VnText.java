package com.ltld.app.legacyvault.utility;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Chuẩn hoá chữ tiếng Việt để so sánh: bỏ dấu, đ → d, in hoa, gộp khoảng trắng. */
public final class VnText {

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private VnText() {
    }

    /** "  Nguyễn  Văn Đức " → "NGUYEN VAN DUC". Trả chuỗi rỗng khi đầu vào null. */
    public static String fold(String input) {
        if (input == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(input, Normalizer.Form.NFD);
        String noMarks = MARKS.matcher(decomposed).replaceAll("")
                .replace('đ', 'd').replace('Đ', 'D');
        return SPACES.matcher(noMarks.trim()).replaceAll(" ").toUpperCase(Locale.ROOT);
    }

    /** So sánh họ tên không phân biệt dấu, hoa thường, khoảng trắng. */
    public static boolean sameName(String a, String b) {
        String fa = fold(a);
        return !fa.isEmpty() && fa.equals(fold(b));
    }

    /** Họ tên viết hoa đầu từ, giữ dấu: "NGUYỄN VĂN AN" → "Nguyễn Văn An". */
    public static String titleCaseName(String input) {
        if (input == null) {
            return null;
        }
        String[] words = SPACES.matcher(input.trim()).replaceAll(" ").toLowerCase(Locale.ROOT).split(" ");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.appendCodePoint(Character.toUpperCase(w.codePointAt(0)));
            sb.append(w.substring(Character.charCount(w.codePointAt(0))));
        }
        return sb.toString();
    }
}
