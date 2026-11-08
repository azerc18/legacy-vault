package com.ltld.app.legacyvault.utility.mail;

import com.ltld.app.legacyvault.utility.mail.MailText.Txt;

import java.util.ArrayList;
import java.util.List;

/** Nội dung một email theo khối; MailLayout dựng ra HTML và text thường từ cùng nội dung này. */
public final class MailContent {

    public enum Tone { INFO, SUCCESS, WARN, DANGER, FORMAL }

    sealed interface Block permits Para, Note, Steps, Rows, Link {}
    record Para(Txt text) implements Block {}
    record Note(Txt text) implements Block {}
    record Steps(String title, List<Txt> items) implements Block {}
    record Rows(List<String[]> rows) implements Block {}
    record Link(String label, String url) implements Block {}

    String subject;
    String preheader;
    Tone tone = Tone.INFO;
    String kicker;
    Txt greeting;
    Txt headline;
    // Ô hành động (chọn một trong hai): mã OTP hoặc nút
    String code;
    String codeLabel;
    String buttonLabel;
    String buttonUrl;
    Txt insetCaption;
    final List<Block> blocks = new ArrayList<>();

    public static MailContent create(String subject) {
        MailContent c = new MailContent();
        c.subject = subject;
        return c;
    }

    public MailContent preheader(String s) { this.preheader = s; return this; }
    public MailContent kicker(Tone tone, String label) { this.tone = tone; this.kicker = label; return this; }
    public MailContent greeting(Txt t) { this.greeting = t; return this; }
    public MailContent headline(Txt t) { this.headline = t; return this; }
    public MailContent code(String label, String code, Txt caption) {
        this.codeLabel = label; this.code = code; this.insetCaption = caption; return this;
    }
    /** url null (chưa cấu hình frontend) thì bỏ nút. */
    public MailContent button(String label, String url, Txt caption) {
        if (url != null) { this.buttonLabel = label; this.buttonUrl = url; this.insetCaption = caption; }
        return this;
    }
    public MailContent para(Txt t) { blocks.add(new Para(t)); return this; }
    public MailContent note(Txt t) { blocks.add(new Note(t)); return this; }
    public MailContent steps(String title, List<Txt> items) { blocks.add(new Steps(title, items)); return this; }
    public MailContent rows(List<String[]> rows) { blocks.add(new Rows(rows)); return this; }
    /** Dòng "nhãn: link" (plain) / link chữ (HTML). url null thì bỏ. */
    public MailContent link(String label, String url) { if (url != null) blocks.add(new Link(label, url)); return this; }

    public String subject() { return subject; }
}
