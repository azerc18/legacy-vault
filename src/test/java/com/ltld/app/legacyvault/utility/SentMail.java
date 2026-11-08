package com.ltld.app.legacyvault.utility;

import com.ltld.app.legacyvault.enums.AppLanguage;
import com.ltld.app.legacyvault.service.userservice.UserLanguageResolver;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

/** Hỗ trợ test: dựng MimeMessage thật cho JavaMailSender giả và đọc lại thư đã gửi (người nhận, chủ đề, chữ trơn, HTML). */
final class SentMail {

    private final String[] to;
    private final String subject;
    private final String text;
    private final String html;
    private final String from;

    private SentMail(String[] to, String subject, String text, String html, String from) {
        this.to = to;
        this.subject = subject;
        this.text = text;
        this.html = html;
        this.from = from;
    }

    String[] getTo() {
        return to;
    }

    String getSubject() {
        return subject;
    }

    /** Bản chữ trơn. */
    String getText() {
        return text;
    }

    String getFrom() {
        return from;
    }

    String getHtml() {
        return html;
    }

    static void stub(JavaMailSender sender, UserLanguageResolver resolver, AppLanguage language) {
        lenient().when(sender.createMimeMessage())
                .thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
        lenient().when(resolver.forEmail(any())).thenReturn(language);
    }

    static SentMail captureOne(JavaMailSender sender) {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(captor.capture());
        return of(captor.getValue());
    }

    static SentMail of(MimeMessage message) {
        try {
            message.saveChanges();
            StringBuilder text = new StringBuilder();
            StringBuilder html = new StringBuilder();
            walk(message, text, html);
            jakarta.mail.Address[] recipients = message.getRecipients(Message.RecipientType.TO);
            String[] to = new String[recipients.length];
            for (int i = 0; i < recipients.length; i++) {
                to[i] = recipients[i].toString();
            }
            return new SentMail(to, message.getSubject(), text.toString(), html.toString(),
                    message.getFrom() == null || message.getFrom().length == 0 ? "" : message.getFrom()[0].toString());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void walk(Part part, StringBuilder text, StringBuilder html) throws Exception {
        if (part.isMimeType("text/plain")) {
            text.append(part.getContent());
        } else if (part.isMimeType("text/html")) {
            html.append(part.getContent());
        } else if (part.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) part.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                walk(mp.getBodyPart(i), text, html);
            }
        }
    }
}
