package com.ltld.app.legacyvault.utility;

import com.ltld.app.legacyvault.enums.AppLanguage;
import com.ltld.app.legacyvault.service.userservice.UserLanguageResolver;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailSenderPaymentMailsTest {

    @Mock private JavaMailSender javaMailSender;
    @Mock private UserLanguageResolver languageResolver;
    @InjectMocks private EmailSender emailSender;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailSender, "frontendBaseUrl", "https://legacyvault.test");
    }

    private static boolean hasPdf(Part part) throws Exception {
        if (part.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) part.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                if (hasPdf(mp.getBodyPart(i))) {
                    return true;
                }
            }
            return false;
        }
        return part.isMimeType("application/pdf") && "hoa-don-R-1.pdf".equals(part.getFileName());
    }

    private MimeMessage captured() {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(javaMailSender).send(captor.capture());
        return captor.getValue();
    }

    @Test
    void paymentSucceeded_attachesPdfAndFormatsAmount() throws Exception {
        SentMail.stub(javaMailSender, languageResolver, AppLanguage.EN);

        emailSender.sendPaymentSucceededNotice("an@example.com", "An", "VIP", 99000L, "R-1", new byte[]{1, 2});

        MimeMessage message = captured();
        message.saveChanges();
        assertThat(hasPdf(message)).isTrue();
        assertThat(SentMail.of(message).getText()).contains("99,000 VND", "R-1", "attached");
    }

    @Test
    void paymentSucceeded_withoutPdf_doesNotClaimAttachment() throws Exception {
        SentMail.stub(javaMailSender, languageResolver, AppLanguage.VI);

        emailSender.sendPaymentSucceededNotice("an@example.com", "An", "VIP", 99000L, "R-1", null);

        MimeMessage message = captured();
        message.saveChanges();
        assertThat(hasPdf(message)).isFalse();
        assertThat(SentMail.of(message).getText()).doesNotContain("đính kèm");
    }

    @Test
    void newMailKeys_existInBothLanguages() throws IOException {
        Properties vi = load("mail/mail_vi.properties");
        Properties en = load("mail/mail_en.properties");

        assertThat(en.stringPropertyNames().stream().filter(k -> !vi.containsKey(k)).collect(Collectors.toSet())).isEmpty();
        for (String prefix : new String[]{"mail.paymentOk", "mail.paymentFailed", "mail.vipActive", "mail.subExpired"}) {
            assertThat(en).containsKeys(prefix + ".subject", prefix + ".title");
            assertThat(vi).containsKeys(prefix + ".subject", prefix + ".title");
        }
    }

    private static Properties load(String path) throws IOException {
        Properties p = new Properties();
        try (InputStream in = EmailSenderPaymentMailsTest.class.getClassLoader().getResourceAsStream(path)) {
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return p;
    }
}
