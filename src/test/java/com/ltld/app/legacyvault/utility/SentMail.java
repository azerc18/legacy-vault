package com.ltld.app.legacyvault.utility;

import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Properties;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

/** Test helper: email HTML (MimeMessage) mà EmailSender gửi, đọc lại thành to / subject / text / html. */
final class SentMail {

    private final String[] to;
    private final String subject;
    private final String text;
    private final String html;

    private SentMail(String[] to, String subject, String text, String html) {
        this.to = to;
        this.subject = subject;
        this.text = text;
        this.html = html;
    }

    /** Cho JavaMailSender (mock) tạo MimeMessage thật; chạy tiếng Anh để khớp các chuỗi kiểm tra. */
    static void prepare(JavaMailSender javaMailSender, EmailSender emailSender) {
        lenient().when(javaMailSender.createMimeMessage())
                .thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
        ReflectionTestUtils.setField(emailSender, "defaultLanguage", "en");
    }

    static SentMail capture(JavaMailSender javaMailSender) {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender).send(captor.capture());
        try {
            // Ghi ra byte rồi đọc lại để đúng như mail thật được gửi đi (đã mã hoá, đã ghép multipart)
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            captor.getValue().writeTo(out);
            MimeMessage m = new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(out.toByteArray()));
            String[] to = Arrays.stream(m.getRecipients(Message.RecipientType.TO))
                    .map(a -> ((InternetAddress) a).getAddress()).toArray(String[]::new);
            StringBuilder text = new StringBuilder();
            StringBuilder html = new StringBuilder();
            collect(m, text, html);
            return new SentMail(to, m.getSubject(), text.toString(), html.toString());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void collect(Part part, StringBuilder text, StringBuilder html) throws Exception {
        if (part.isMimeType("text/plain")) {
            text.append(part.getContent());
        } else if (part.isMimeType("text/html")) {
            html.append(part.getContent());
        } else if (part.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) part.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                BodyPart bp = mp.getBodyPart(i);
                collect(bp, text, html);
            }
        }
    }

    String[] getTo() { return to; }
    String getSubject() { return subject; }
    String getText() { return text; }
    String getHtml() { return html; }
}
