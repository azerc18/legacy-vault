package com.ltld.app.legacyvault.utility;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dựng HTML cho email của LegacyVault. Gmail/Outlook bỏ thẻ style và CSS ngoài nên mọi kiểu đều viết inline,
 * bố cục dùng bảng. Mọi chữ do người gọi truyền vào (đã dịch); lớp này không tự cứng chữ nào ngoài tên thương hiệu.
 */
public final class MailHtml {

    /** Nhóm giao diện: quyết định màu nhấn của thẻ. */
    public enum Style {
        CODE("#1f4e79"),
        INVITE("#1f4e79"),
        SECURITY("#b3261e"),
        SUCCESS("#2e7d32"),
        URGENT("#c77700"),
        NOTICE("#5f6b7a"),
        BILLING("#1f4e79"),
        LEGACY("#6b4e2e"),
        CASE("#1f4e79"),
        DIGEST("#1f4e79");

        private final String accent;

        Style(String accent) {
            this.accent = accent;
        }

        String accent() {
            return accent;
        }
    }

    private static final Pattern URL = Pattern.compile("(https?://[^\\s<>\"]+)");
    private static final Pattern LABELLED_LINK = Pattern.compile("^([^\\n]*?):\\s*(https?://\\S+)$");

    private MailHtml() {
    }

    /**
     * @param paragraphs mỗi phần tử là một đoạn; đoạn dạng "Nhãn: https://..." thành nút, URL trần thành link
     * @param code       mã OTP (null nếu không có)
     * @param footerNote dòng nhỏ cuối thư (null nếu không có)
     * @param brandTag   câu chân trang
     */
    public static String render(Style style, String title, List<String> paragraphs, String code,
                                String footerNote, String brandTag) {
        String accent = style.accent();
        StringBuilder h = new StringBuilder(2048);
        h.append("<!DOCTYPE html><html><body style=\"margin:0;padding:0;background:#f3f1ec;\">")
                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" ")
                .append("style=\"background:#f3f1ec;padding:24px 12px;\"><tr><td align=\"center\">")
                .append("<table role=\"presentation\" width=\"560\" cellpadding=\"0\" cellspacing=\"0\" ")
                .append("style=\"max-width:560px;width:100%;background:#ffffff;border:1px solid #e0dcd2;")
                .append("border-top:4px solid ").append(accent).append(";")
                .append("font-family:Georgia,'Times New Roman',serif;color:#1c2430;\">");

        h.append("<tr><td style=\"padding:22px 28px 8px 28px;font-size:13px;letter-spacing:2px;color:")
                .append(accent).append(";font-weight:bold;\">LEGACYVAULT</td></tr>");

        h.append("<tr><td style=\"padding:4px 28px 12px 28px;font-size:22px;line-height:1.3;font-weight:bold;\">")
                .append(esc(title)).append("</td></tr>");

        h.append("<tr><td style=\"padding:0 28px 8px 28px;font-size:15px;line-height:1.6;\">");
        for (String p : paragraphs) {
            if (p == null || p.isBlank()) {
                continue;
            }
            Matcher m = LABELLED_LINK.matcher(p.trim());
            if (m.matches()) {
                h.append(button(m.group(2), m.group(1), accent));
            } else {
                h.append("<p style=\"margin:0 0 12px 0;\">").append(linkify(p)).append("</p>");
            }
        }
        h.append("</td></tr>");

        if (code != null && !code.isBlank()) {
            h.append("<tr><td align=\"center\" style=\"padding:4px 28px 16px 28px;\">")
                    .append("<div style=\"display:inline-block;padding:14px 28px;border:1px dashed ").append(accent)
                    .append(";background:#faf8f3;font-family:'Courier New',monospace;font-size:30px;")
                    .append("letter-spacing:8px;font-weight:bold;color:").append(accent).append(";\">")
                    .append(esc(code)).append("</div></td></tr>");
        }

        if (footerNote != null && !footerNote.isBlank()) {
            h.append("<tr><td style=\"padding:0 28px 16px 28px;font-size:13px;line-height:1.5;color:#5f6b7a;\">")
                    .append(esc(footerNote).replace("\n", "<br>")).append("</td></tr>");
        }

        h.append("<tr><td style=\"padding:14px 28px;border-top:1px solid #e0dcd2;font-size:12px;color:#8a8f98;\">")
                .append(esc(brandTag)).append("</td></tr>")
                .append("</table></td></tr></table></body></html>");
        return h.toString();
    }

    /** Bản chữ trơn dự phòng, cùng nội dung với bản HTML. */
    public static String renderText(String title, List<String> paragraphs, String code, String footerNote) {
        List<String> parts = new ArrayList<>();
        parts.add(title);
        for (String p : paragraphs) {
            if (p != null && !p.isBlank()) {
                parts.add(p);
            }
        }
        if (code != null && !code.isBlank()) {
            parts.add(code);
        }
        if (footerNote != null && !footerNote.isBlank()) {
            parts.add(footerNote);
        }
        return String.join("\n\n", parts);
    }

    private static String button(String url, String label, String accent) {
        return "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:0 0 14px 0;\"><tr>"
                + "<td style=\"background:" + accent + ";\">"
                + "<a href=\"" + esc(url) + "\" style=\"display:inline-block;padding:11px 22px;"
                + "font-family:Arial,sans-serif;font-size:14px;font-weight:bold;color:#ffffff;text-decoration:none;\">"
                + esc(label) + "</a></td></tr></table>";
    }

    private static String linkify(String text) {
        StringBuilder out = new StringBuilder();
        Matcher m = URL.matcher(text);
        int last = 0;
        while (m.find()) {
            out.append(esc(text.substring(last, m.start())).replace("\n", "<br>"));
            String url = m.group(1);
            out.append("<a href=\"").append(esc(url)).append("\" style=\"color:#1f4e79;\">")
                    .append(esc(url)).append("</a>");
            last = m.end();
        }
        out.append(esc(text.substring(last)).replace("\n", "<br>"));
        return out.toString();
    }

    static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
