package com.ltld.app.legacyvault.utility.mail;

import com.ltld.app.legacyvault.utility.mail.MailContent.Block;
import com.ltld.app.legacyvault.utility.mail.MailContent.Link;
import com.ltld.app.legacyvault.utility.mail.MailContent.Note;
import com.ltld.app.legacyvault.utility.mail.MailContent.Para;
import com.ltld.app.legacyvault.utility.mail.MailContent.Rows;
import com.ltld.app.legacyvault.utility.mail.MailContent.Steps;
import com.ltld.app.legacyvault.utility.mail.MailContent.Tone;
import com.ltld.app.legacyvault.utility.mail.MailText.Txt;

/**
 * Khung HTML chung cho mọi email (bố cục kiểu Steam: khối tối + ô hành động lõm + chữ ký vạch xanh + chân trang sáng),
 * màu lấy theo token của web. Bố cục bằng table + CSS inline vì Gmail/Outlook bỏ CSS ngoài.
 * Luôn kèm bản text thường cho trình đọc mail không hiển thị HTML.
 */
public final class MailLayout {

    // Token màu của web (index.css): brand / secondary / surface
    static final String BRAND = "#0B1020";
    static final String CARD = "#1A2140";
    static final String INSET = "#121831";
    static final String ACCENT = "#3D5AE0";
    static final String ACCENT2 = "#5B76F0";
    static final String SOFT = "#8FA4FF";
    static final String TEXT = "#E4E8F5";
    static final String MUTED = "#A9B1CC";
    static final String RULE = "#2B3560";
    static final String PAPER = "#F3F5FA";
    static final String INK = "#0B1020";
    static final String INK2 = "#4A5272";
    static final String FONT = "'Plus Jakarta Sans','Segoe UI',Helvetica,Arial,sans-serif";

    private MailLayout() {
    }

    /** logoUrl: URL tuyệt đối tới logo PNG (Gmail chặn ảnh nhúng data:). siteUrl null thì bỏ link trang chủ. */
    public record Brand(String logoUrl, String siteUrl) {
    }

    public record Rendered(String subject, String html, String plain) {
    }

    public static Rendered render(MailContent c, MailText t, Brand brand) {
        return new Rendered(c.subject, html(c, t, brand), plain(c, t));
    }

    /* ======================== Text thường ======================== */

    static String plain(MailContent c, MailText t) {
        StringBuilder sb = new StringBuilder();
        if (c.greeting != null) sb.append(c.greeting.plain()).append("\n\n");
        if (c.headline != null) sb.append(c.headline.plain()).append("\n");
        if (c.code != null) sb.append(c.code).append("\n");
        if (c.buttonUrl != null) sb.append(c.buttonLabel).append(": ").append(c.buttonUrl).append("\n");
        if (c.insetCaption != null) sb.append(c.insetCaption.plain()).append("\n");
        for (Block b : c.blocks) {
            sb.append("\n");
            switch (b) {
                case Para p -> sb.append(p.text().plain()).append("\n");
                case Note n -> sb.append(n.text().plain()).append("\n");
                case Link l -> sb.append(l.label()).append(": ").append(l.url()).append("\n");
                case Rows r -> r.rows().forEach(row -> sb.append(row[0]).append(": ").append(row[1]).append("\n"));
                case Steps s -> {
                    sb.append(s.title()).append("\n");
                    for (int i = 0; i < s.items().size(); i++) {
                        sb.append(i + 1).append(". ").append(s.items().get(i).plain()).append("\n");
                    }
                }
            }
        }
        sb.append("\n").append(t.plain("common.signoff")).append("\n").append(t.plain("common.team")).append("\n\n")
                .append("--\n").append(t.plain("common.footer.auto"));
        return sb.toString();
    }

    /* ======================== HTML ======================== */

    static String html(MailContent c, MailText t, Brand brand) {
        StringBuilder b = new StringBuilder(8192);
        b.append("<!doctype html><html lang=\"").append(t.locale().getLanguage()).append("\"><head>")
                .append("<meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
                .append("<meta name=\"color-scheme\" content=\"light dark\"><title>").append(esc(c.subject)).append("</title>")
                .append("<style>@media (max-width:620px){.lv-wrap{width:100%!important}.lv-pad{padding-left:24px!important;padding-right:24px!important}")
                .append(".lv-h1{font-size:22px!important;line-height:30px!important}.lv-col{display:block!important;width:100%!important;padding-bottom:16px!important}")
                .append(".lv-otp{width:34px!important;margin:0 2px!important;font-size:22px!important}}</style></head>")
                .append("<body style=\"margin:0;padding:0;background:#ffffff;\">");
        if (c.preheader != null) {
            b.append("<div style=\"display:none;max-height:0;overflow:hidden;opacity:0;\">").append(esc(c.preheader)).append("</div>");
        }
        b.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"background:#ffffff;\">")
                .append("<tr><td align=\"center\" style=\"padding:24px 12px 0;\">")
                .append("<table role=\"presentation\" class=\"lv-wrap\" width=\"600\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"width:600px;max-width:600px;\">");

        // ----- Khối tối -----
        b.append("<tr><td class=\"lv-pad\" style=\"background:").append(BRAND).append(";background-image:linear-gradient(160deg,")
                .append(CARD).append(" 0%,").append(BRAND).append(" 55%);padding:56px 64px 48px;\">");
        header(b, t, brand);
        if (c.kicker != null) {
            b.append("<div style=\"padding-top:40px;font-family:").append(FONT).append(";font-size:11px;font-weight:800;letter-spacing:3px;text-transform:uppercase;color:")
                    .append(toneColor(c.tone)).append(";\">").append(esc(c.kicker)).append("</div>");
        }
        String top = c.kicker != null ? "14px" : "44px";
        if (c.greeting != null) {
            b.append("<div style=\"font-family:").append(FONT).append(";color:").append(MUTED)
                    .append(";font-size:28px;font-weight:700;line-height:36px;padding-top:").append(top).append(";\">")
                    .append(c.greeting.html()).append("</div>");
            top = "18px";
        }
        if (c.headline != null) {
            b.append("<div class=\"lv-h1\" style=\"font-family:").append(FONT).append(";color:#ffffff;font-size:24px;font-weight:700;line-height:32px;padding-top:")
                    .append(top).append(";\">").append(c.headline.html()).append("</div>");
        }
        inset(b, c);
        for (Block block : c.blocks) {
            block(b, block);
        }
        // Chữ ký vạch xanh
        b.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"margin-top:40px;\"><tr>")
                .append("<td style=\"border-left:3px solid ").append(ACCENT2).append(";padding:14px 0 14px 28px;font-family:").append(FONT)
                .append(";color:#ffffff;font-size:14px;line-height:21px;\">").append(esc(t.plain("common.signoff")))
                .append("<br><strong>").append(esc(t.plain("common.team"))).append("</strong></td></tr></table>");
        b.append("</td></tr>");

        footer(b, t, brand);
        b.append("</table></td></tr></table></body></html>");
        return b.toString();
    }

    private static void header(StringBuilder b, MailText t, Brand brand) {
        b.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tr>")
                .append("<td style=\"vertical-align:middle;\"><img src=\"").append(esc(brand.logoUrl()))
                .append("\" width=\"56\" height=\"56\" alt=\"LegacyVault\" style=\"display:block;border:0;width:56px;height:56px;\"></td>")
                .append("<td style=\"vertical-align:middle;padding-left:14px;font-family:").append(FONT).append(";\">")
                .append("<div style=\"color:#ffffff;font-size:16px;font-weight:800;letter-spacing:4px;\">LEGACYVAULT</div>")
                .append("<div style=\"color:").append(MUTED).append(";font-size:11px;letter-spacing:2px;text-transform:uppercase;padding-top:2px;\">")
                .append(esc(t.plain("common.tagline"))).append("</div></td></tr></table>");
    }

    private static void inset(StringBuilder b, MailContent c) {
        if (c.code == null && c.buttonUrl == null) return;
        b.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"margin-top:26px;\">")
                .append("<tr><td align=\"center\" style=\"background:").append(INSET).append(";border:1px solid ").append(RULE)
                .append(";border-radius:8px;padding:30px 20px;\">");
        if (c.code != null) {
            if (c.codeLabel != null) {
                b.append("<div style=\"font-family:").append(FONT).append(";color:").append(MUTED)
                        .append(";font-size:11px;letter-spacing:3px;text-transform:uppercase;padding-bottom:14px;\">").append(esc(c.codeLabel)).append("</div>");
            }
            b.append("<div>");
            for (char ch : c.code.toCharArray()) {
                b.append("<span class=\"lv-otp\" style=\"display:inline-block;width:40px;height:52px;line-height:52px;margin:0 4px;background:")
                        .append(CARD).append(";border:1px solid ").append(ACCENT2)
                        .append(";border-radius:6px;color:#ffffff;font-family:Consolas,Menlo,monospace;font-size:26px;font-weight:700;\">")
                        .append(esc(String.valueOf(ch))).append("</span>");
            }
            b.append("</div>");
        } else {
            String url = esc(c.buttonUrl);
            String label = esc(c.buttonLabel.toUpperCase(java.util.Locale.ROOT));
            // Nút "chống đạn": VML cho Outlook desktop, thẻ <a> cho phần còn lại
            b.append("<!--[if mso]><v:roundrect xmlns:v=\"urn:schemas-microsoft-com:vml\" href=\"").append(url)
                    .append("\" style=\"height:48px;v-text-anchor:middle;width:340px;\" arcsize=\"8%\" fillcolor=\"").append(ACCENT)
                    .append("\" stroke=\"f\"><center style=\"color:#ffffff;font-family:Arial;font-size:15px;\">").append(label)
                    .append("</center></v:roundrect><![endif]--><!--[if !mso]><!-->")
                    .append("<a href=\"").append(url).append("\" style=\"display:inline-block;background:").append(ACCENT)
                    .append(";background-image:linear-gradient(90deg,").append(ACCENT2).append(",").append(ACCENT)
                    .append(");color:#ffffff;font-family:").append(FONT)
                    .append(";font-size:15px;font-weight:600;letter-spacing:1.5px;text-decoration:none;padding:14px 48px;border-radius:4px;\">")
                    .append(label).append("</a><!--<![endif]-->");
        }
        if (c.insetCaption != null) {
            b.append("<div style=\"font-family:").append(FONT).append(";color:").append(MUTED).append(";font-size:12px;padding-top:14px;\">")
                    .append(c.insetCaption.html()).append("</div>");
        }
        b.append("</td></tr></table>");
    }

    private static void block(StringBuilder b, Block block) {
        String p = "font-family:" + FONT + ";color:" + TEXT + ";font-size:15px;line-height:23px;padding-top:20px;";
        switch (block) {
            case Para para -> b.append("<div style=\"").append(p).append("\">").append(nl2br(para.text().html())).append("</div>");
            case Note note -> b.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"margin-top:22px;\"><tr>")
                    .append("<td style=\"background:rgba(251,191,87,0.08);border-left:3px solid #FBBF57;border-radius:4px;padding:12px 16px;font-family:")
                    .append(FONT).append(";color:").append(TEXT).append(";font-size:13px;line-height:20px;\">")
                    .append(nl2br(note.text().html())).append("</td></tr></table>");
            case Link link -> b.append("<div style=\"").append(p).append("\">").append(esc(link.label())).append(": <a href=\"")
                    .append(esc(link.url())).append("\" style=\"color:").append(SOFT).append(";word-break:break-all;\">")
                    .append(esc(link.url())).append("</a></div>");
            case Rows rows -> {
                b.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"margin-top:22px;border:1px solid ")
                        .append(RULE).append(";border-radius:8px;\">");
                for (int i = 0; i < rows.rows().size(); i++) {
                    String[] r = rows.rows().get(i);
                    String border = i == 0 ? "" : "border-top:1px solid " + RULE + ";";
                    b.append("<tr><td style=\"").append(border).append("padding:12px 16px;font-family:").append(FONT).append(";color:").append(MUTED)
                            .append(";font-size:13px;\">").append(esc(r[0])).append("</td><td align=\"right\" style=\"").append(border)
                            .append("padding:12px 16px;font-family:").append(FONT).append(";color:#ffffff;font-size:14px;font-weight:700;\">")
                            .append(esc(r[1])).append("</td></tr>");
                }
                b.append("</table>");
            }
            case Steps steps -> {
                b.append("<div style=\"").append(p).append("font-weight:700;color:#ffffff;\">").append(esc(steps.title())).append("</div>");
                for (int i = 0; i < steps.items().size(); i++) {
                    b.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"margin-top:10px;\"><tr>")
                            .append("<td style=\"vertical-align:top;\"><div style=\"width:24px;height:24px;line-height:24px;border-radius:12px;text-align:center;background:")
                            .append(ACCENT).append(";color:#ffffff;font-family:").append(FONT).append(";font-size:12px;font-weight:700;\">")
                            .append(i + 1).append("</div></td><td style=\"padding-left:12px;font-family:").append(FONT).append(";color:").append(TEXT)
                            .append(";font-size:14px;line-height:22px;\">").append(steps.items().get(i).html()).append("</td></tr></table>");
                }
            }
        }
    }

    private static void footer(StringBuilder b, MailText t, Brand brand) {
        b.append("<tr><td class=\"lv-pad\" style=\"background:").append(PAPER).append(";padding:36px 64px 12px;font-family:").append(FONT)
                .append(";color:").append(INK).append(";font-size:14px;line-height:21px;\">")
                .append(esc(t.plain("common.footer.sentTo"))).append("<br><br>").append(esc(t.plain("common.footer.auto")));
        if (brand.siteUrl() != null) {
            b.append("<br><br><a href=\"").append(esc(brand.siteUrl())).append("\" style=\"color:").append(ACCENT).append(";text-decoration:underline;\">")
                    .append(esc(brand.siteUrl())).append("</a>");
        }
        b.append("</td></tr>")
                .append("<tr><td class=\"lv-pad\" style=\"background:").append(PAPER).append(";padding:24px 64px 32px;\">")
                .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"border-top:1px solid #C3CAE0;\"><tr>")
                .append("<td class=\"lv-col\" width=\"50%\" style=\"vertical-align:top;padding-top:24px;\">")
                .append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tr><td style=\"background:").append(BRAND)
                .append(";border-radius:10px;padding:8px;\"><img src=\"").append(esc(brand.logoUrl()))
                .append("\" width=\"28\" height=\"28\" alt=\"\" style=\"display:block;border:0;\"></td>")
                .append("<td style=\"padding-left:10px;font-family:").append(FONT).append(";color:").append(INK)
                .append(";font-size:14px;font-weight:800;letter-spacing:3px;\">LEGACYVAULT</td></tr></table></td>")
                .append("<td class=\"lv-col\" width=\"50%\" style=\"vertical-align:top;padding-top:24px;font-family:").append(FONT)
                .append(";color:").append(INK2).append(";font-size:11px;line-height:17px;\">").append(esc(t.plain("common.footer.about")))
                .append("<br><br><strong style=\"color:").append(INK).append(";\">© 2026 LegacyVault</strong><br>")
                .append(esc(t.plain("common.footer.org"))).append("</td></tr></table></td></tr>");
    }

    private static String toneColor(Tone tone) {
        return switch (tone) {
            case SUCCESS -> "#6EE7A0";
            case WARN -> "#FBBF57";
            case DANGER -> "#FF8A80";
            case FORMAL -> "#E9C46A";
            case INFO -> SOFT;
        };
    }

    /* ======================== Tiện ích ======================== */

    static String highlight(String value) {
        return "<strong style=\"color:" + SOFT + ";font-weight:700;\">" + esc(value) + "</strong>";
    }

    static String nl2br(String html) {
        return html.replace("\n", "<br>");
    }

    public static String esc(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '&' -> out.append("&amp;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(ch);
            }
        }
        return out.toString();
    }
}
