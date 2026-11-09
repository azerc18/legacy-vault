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

    /** Nhóm giao diện: quyết định màu nhấn (vạch trên cùng, viền ô mã) trên nền tối. */
    public enum Style {
        CODE("#8FA4FF"),
        INVITE("#8FA4FF"),
        SECURITY("#FF8A80"),
        SUCCESS("#6EE7A0"),
        URGENT("#FBBF57"),
        NOTICE("#A9B1CC"),
        BILLING("#8FA4FF"),
        LEGACY("#E9C46A"),
        CASE("#8FA4FF"),
        DIGEST("#8FA4FF");

        private final String accent;

        Style(String accent) {
            this.accent = accent;
        }

        String accent() {
            return accent;
        }
    }

    // Màu theo token của web (frontend/src/index.css): brand / secondary / surface
    private static final String BRAND = "#0B1020";
    private static final String CARD = "#1A2140";
    private static final String INSET = "#121831";
    private static final String ACCENT = "#3D5AE0";
    private static final String ACCENT2 = "#5B76F0";
    private static final String SOFT = "#8FA4FF";
    private static final String TEXT = "#E4E8F5";
    private static final String MUTED = "#A9B1CC";
    private static final String RULE = "#2B3560";
    // Cả thư dùng nền tối + chữ sáng (không còn phần nền sáng): ứng dụng mail bật chế độ tối không đảo màu
    // thành chữ đen trên nền đen. Màu nền đặt cả bằng CSS lẫn thuộc tính bgcolor cho Outlook.
    private static final String FOOT = "#121831";
    private static final String FONT = "'Plus Jakarta Sans','Segoe UI',Helvetica,Arial,sans-serif";
    // Gmail chặn ảnh nhúng nên logo phải là URL tuyệt đối
    static final String LOGO_URL = "https://legacyvault.codes/brand/logo-mark.png";
    static final String SITE_URL = "https://legacyvault.codes";

    private static final Pattern URL = Pattern.compile("(https?://[^\\s<>\"]+)");
    private static final Pattern LABELLED_LINK = Pattern.compile("^([^\\n]*?):\\s*(https?://\\S+)$");

    private MailHtml() {
    }

    /**
     * Bố cục: khối tối (logo, tiêu đề, các đoạn; đoạn "Nhãn: https://..." thành nút nằm trong ô lõm; mã OTP sau các
     * đoạn, mỗi chữ số một ô), ghi chú cuối thư dạng hộp vàng, rồi chân trang nền tối hơn. Bảng + CSS inline cho Gmail/Outlook.
     *
     * @param paragraphs mỗi phần tử là một đoạn; đoạn dạng "Nhãn: https://..." thành nút, URL trần thành link
     * @param code       mã OTP (null nếu không có)
     * @param footerNote dòng nhỏ cuối thư (null nếu không có)
     * @param brandTag   câu chân trang
     */
    public static String render(Style style, String title, List<String> paragraphs, String code,
                                String footerNote, String brandTag) {
        String accent = style.accent();
        boolean hasCode = code != null && !code.isBlank();
        StringBuilder h = new StringBuilder(6144);
        h.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
                // Báo cho Apple Mail / Outlook / Gmail biết thư đã hợp nền tối, đừng tự đảo màu
                .append("<meta name=\"color-scheme\" content=\"light dark\"><meta name=\"supported-color-schemes\" content=\"light dark\">")
                .append("<style>:root{color-scheme:light dark;supported-color-schemes:light dark}")
                .append("body,.lv-body{background:").append(BRAND).append("!important;color:").append(TEXT).append("!important}")
                .append("@media (max-width:620px){.lv-wrap{width:100%!important}")
                .append(".lv-pad{padding-left:24px!important;padding-right:24px!important}")
                .append(".lv-h1{font-size:22px!important;line-height:30px!important}")
                .append(".lv-otp{width:34px!important;margin:0 2px!important;font-size:22px!important}}</style></head>")
                .append("<body class=\"lv-body\" bgcolor=\"").append(BRAND).append("\" style=\"margin:0;padding:0;background:")
                .append(BRAND).append(";color:").append(TEXT).append(";\">");
        // Dòng xem trước trong hộp thư (ẩn khi mở thư)
        h.append("<div style=\"display:none;max-height:0;overflow:hidden;opacity:0;\">").append(esc(title));
        if (hasCode) h.append(": ").append(esc(code));
        h.append("</div>");

        h.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" bgcolor=\"").append(BRAND)
                .append("\" style=\"background:").append(BRAND).append(";\">")
                .append("<tr><td align=\"center\" style=\"padding:24px 12px 0;\">")
                .append("<table role=\"presentation\" class=\"lv-wrap\" width=\"600\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" ")
                .append("style=\"width:600px;max-width:600px;\">");

        // ----- Khối tối -----
        h.append("<tr><td style=\"height:4px;line-height:4px;font-size:0;background:").append(accent).append(";\">&nbsp;</td></tr>")
                .append("<tr><td class=\"lv-pad\" bgcolor=\"").append(BRAND).append("\" style=\"background:").append(BRAND)
                .append(";color:").append(TEXT).append(";background-image:linear-gradient(160deg,")
                .append(CARD).append(" 0%,").append(BRAND).append(" 55%);padding:52px 64px 48px;\">");
        h.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tr>")
                .append("<td style=\"vertical-align:middle;\"><img src=\"").append(LOGO_URL)
                .append("\" width=\"52\" height=\"52\" alt=\"LegacyVault\" style=\"display:block;border:0;width:52px;height:52px;\"></td>")
                .append("<td style=\"vertical-align:middle;padding-left:14px;font-family:").append(FONT)
                .append(";color:#ffffff;font-size:16px;font-weight:800;letter-spacing:4px;\">LEGACYVAULT</td></tr></table>");

        h.append("<div class=\"lv-h1\" style=\"font-family:").append(FONT)
                .append(";color:#ffffff;font-size:26px;font-weight:700;line-height:34px;padding-top:40px;\">")
                .append(esc(title)).append("</div>");

        for (String p : paragraphs) {
            if (p == null || p.isBlank()) {
                continue;
            }
            Matcher m = LABELLED_LINK.matcher(p.trim());
            if (m.matches()) {
                h.append(inset(button(m.group(2), m.group(1))));
            } else {
                h.append("<div style=\"font-family:").append(FONT).append(";color:").append(TEXT)
                        .append(";font-size:15px;line-height:23px;padding-top:20px;\">").append(linkify(p)).append("</div>");
            }
        }
        // Mã nằm sau các đoạn (thân thư viết "dùng mã bên dưới")
        if (hasCode) {
            h.append(inset(codeBoxes(code, accent)));
        }
        if (footerNote != null && !footerNote.isBlank()) {
            h.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"margin-top:24px;\"><tr>")
                    .append("<td bgcolor=\"#2A2416\" style=\"background:#2A2416;border-left:3px solid #FBBF57;border-radius:4px;padding:12px 16px;font-family:")
                    .append(FONT).append(";color:").append(TEXT).append(";font-size:13px;line-height:20px;\">")
                    .append(esc(footerNote).replace("\n", "<br>")).append("</td></tr></table>");
        }
        // Chữ ký vạch xanh (tên thương hiệu, không phụ thuộc ngôn ngữ)
        h.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"margin-top:36px;\"><tr>")
                .append("<td style=\"border-left:3px solid ").append(ACCENT2).append(";padding:10px 0 10px 24px;font-family:").append(FONT)
                .append(";color:#ffffff;font-size:14px;font-weight:700;letter-spacing:1px;\">LegacyVault</td></tr></table>");
        h.append("</td></tr>");

        // ----- Chân trang (nền tối hơn một bậc) -----
        h.append("<tr><td class=\"lv-pad\" bgcolor=\"").append(FOOT).append("\" style=\"background:").append(FOOT)
                .append(";border-top:1px solid ").append(RULE).append(";padding:28px 64px 28px;\">")
                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tr>")
                .append("<td style=\"vertical-align:top;width:44px;\"><div style=\"background:").append(CARD)
                .append(";border-radius:10px;padding:7px;width:28px;\"><img src=\"").append(LOGO_URL)
                .append("\" width=\"28\" height=\"28\" alt=\"\" style=\"display:block;border:0;\"></div></td>")
                .append("<td style=\"vertical-align:top;padding-left:14px;font-family:").append(FONT).append(";color:").append(MUTED)
                .append(";font-size:12px;line-height:18px;\">").append(esc(brandTag))
                .append("<br><a href=\"").append(SITE_URL).append("\" style=\"color:").append(SOFT)
                .append(";font-weight:700;text-decoration:underline;\">legacyvault.codes</a>")
                .append("<br><span style=\"color:#ffffff;font-weight:700;\">&copy; 2026 LegacyVault</span>")
                .append("</td></tr></table></td></tr>");

        h.append("</table></td></tr></table></body></html>");
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

    /** Ô lõm nền tối hơn chứa mã hoặc nút. */
    private static String inset(String inner) {
        return "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"margin-top:26px;\">"
                + "<tr><td align=\"center\" bgcolor=\"" + INSET + "\" style=\"background:" + INSET + ";border:1px solid " + RULE
                + ";border-radius:8px;padding:28px 20px;\">" + inner + "</td></tr></table>";
    }

    /** Mỗi chữ số một ô. */
    private static String codeBoxes(String code, String accent) {
        StringBuilder b = new StringBuilder();
        for (char ch : code.trim().toCharArray()) {
            b.append("<span class=\"lv-otp\" style=\"display:inline-block;width:40px;height:52px;line-height:52px;margin:0 4px;background:")
                    .append(CARD).append(";border:1px solid ").append(accent)
                    .append(";border-radius:6px;color:#ffffff;font-family:Consolas,Menlo,'Courier New',monospace;font-size:26px;font-weight:700;\">")
                    .append(esc(String.valueOf(ch))).append("</span>");
        }
        return b.toString();
    }

    /** Nút "chống đạn": VML cho Outlook desktop, thẻ a cho phần còn lại. */
    private static String button(String url, String label) {
        String u = esc(url);
        String l = esc(label);
        return "<!--[if mso]><v:roundrect xmlns:v=\"urn:schemas-microsoft-com:vml\" href=\"" + u
                + "\" style=\"height:46px;v-text-anchor:middle;width:320px;\" arcsize=\"8%\" fillcolor=\"" + ACCENT
                + "\" stroke=\"f\"><center style=\"color:#ffffff;font-family:Arial;font-size:15px;\">" + l
                + "</center></v:roundrect><![endif]--><!--[if !mso]><!-->"
                + "<a href=\"" + u + "\" style=\"display:inline-block;background:" + ACCENT + ";background-image:linear-gradient(90deg,"
                + ACCENT2 + "," + ACCENT + ");color:#ffffff;font-family:" + FONT
                + ";font-size:15px;font-weight:600;letter-spacing:1px;text-decoration:none;padding:13px 44px;border-radius:4px;\">"
                + l + "</a><!--<![endif]-->";
    }

    private static String linkify(String text) {
        StringBuilder out = new StringBuilder();
        Matcher m = URL.matcher(text);
        int last = 0;
        while (m.find()) {
            out.append(esc(text.substring(last, m.start())).replace("\n", "<br>"));
            String url = m.group(1);
            out.append("<a href=\"").append(esc(url)).append("\" style=\"color:").append(SOFT).append(";word-break:break-all;\">")
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
