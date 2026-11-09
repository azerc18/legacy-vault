package com.ltld.app.legacyvault.utility;

import com.ltld.app.legacyvault.enums.AppLanguage;
import com.ltld.app.legacyvault.service.userservice.UserLanguageResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class EmailSenderLifecycleMailsTest {

    @Mock private JavaMailSender javaMailSender;
    @Mock private UserLanguageResolver languageResolver;
    @InjectMocks private EmailSender emailSender;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailSender, "frontendBaseUrl", "https://legacyvault.test");
    }

    @Test
    void newDevice_escapesUserAgentInHtml() {
        SentMail.stub(javaMailSender, languageResolver, AppLanguage.EN);

        emailSender.sendNewDeviceLoginNotice("an@example.com", "An", "<script>x</script>");

        SentMail mail = SentMail.captureOne(javaMailSender);
        assertThat(mail.getSubject()).isEqualTo("Sign-in from a new device - LegacyVault");
        assertThat(mail.getHtml()).doesNotContain("<script>x");
    }

    @Test
    void claimDeadline_showsDaysLeftAndVaultLink() {
        SentMail.stub(javaMailSender, languageResolver, AppLanguage.VI);

        emailSender.sendClaimDeadlineReminder("an@example.com", "An", "Chu", "Kho",
                LocalDateTime.of(2026, 11, 16, 2, 0), 7, UUID.randomUUID());

        assertThat(SentMail.captureOne(javaMailSender).getText()).contains("còn 7 ngày", "Kho", "Chu", "https://legacyvault.test");
    }

    @Test
    void legacyMessageSaved_neverCarriesPrivateContent_andUsesVaultName() {
        SentMail.stub(javaMailSender, languageResolver, AppLanguage.EN);

        emailSender.sendLegacyMessageSavedNotice("an@example.com", "An", "Kho", false);

        assertThat(SentMail.captureOne(javaMailSender).getText()).contains("created a legacy message", "Kho");
    }

    @Test
    void newMailKeys_existInBothLanguages() throws IOException {
        Properties vi = load("mail/mail_vi.properties");
        Properties en = load("mail/mail_en.properties");

        assertThat(en.stringPropertyNames().stream().filter(k -> !vi.containsKey(k)).collect(Collectors.toSet())).isEmpty();
        for (String prefix : new String[]{"mail.newDevice", "mail.messageSaved", "mail.assetVerified", "mail.assetReverify", "mail.claimDeadline",
                "mail.vaultArchived", "mail.taskDone", "mail.requestReopened"}) {
            assertThat(en).containsKeys(prefix + ".subject", prefix + ".title");
            assertThat(vi).containsKeys(prefix + ".subject", prefix + ".title");
        }
    }

    private static Properties load(String path) throws IOException {
        Properties p = new Properties();
        try (InputStream in = EmailSenderLifecycleMailsTest.class.getClassLoader().getResourceAsStream(path)) {
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return p;
    }
}
