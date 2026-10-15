package com.ltld.app.legacyvault.utility;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.TimeZone;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailSenderTest {

    @Mock private JavaMailSender javaMailSender;
    private static final UUID VAULT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    @InjectMocks private EmailSender emailSender;

    private TimeZone originalZone;

    @BeforeEach
    void setUp() {
        originalZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        ReflectionTestUtils.setField(emailSender, "frontendBaseUrl", "http://localhost:3000/");
    }

    @AfterEach
    void tearDown() {
        TimeZone.setDefault(originalZone);
    }

    private SimpleMailMessage sentMessage() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender).send(captor.capture());
        return captor.getValue();
    }

    @Test
    void unlockedNotification_mentionsOwnerVaultDeadlineAndTimezone() {
        emailSender.sendVaultUnlockedNotification("an@example.com", "An", "Bình", "Hồ sơ A",
                LocalDateTime.of(2026, 12, 31, 10, 30), true, VAULT_ID);

        SimpleMailMessage message = sentMessage();
        String text = message.getText();
        assertTrue(message.getTo()[0].equals("an@example.com"));
        assertTrue(text.contains("Hồ sơ \"Hồ sơ A\" của Bình"));
        assertTrue(text.contains("31/12/2026 17:30 (GMT+7)"));
        assertFalse(text.contains("chưa được kích hoạt"));
        assertFalse(text.contains("Mã có hiệu lực"));
    }

    @Test
    void unlockedNotification_pendingAccount_addsRegistrationReminder() {
        emailSender.sendVaultUnlockedNotification("an@example.com", "An", "Bình", "Hồ sơ A",
                LocalDateTime.of(2026, 12, 31, 10, 30), false, VAULT_ID);

        assertTrue(sentMessage().getText().contains("Tài khoản của bạn chưa được kích hoạt"));
    }

    @Test
    void unlockedNotification_propagatesSendFailure() {
        doThrow(new MailSendException("smtp down")).when(javaMailSender).send(any(SimpleMailMessage.class));

        assertThrows(MailSendException.class, () -> emailSender.sendVaultUnlockedNotification(
                "an@example.com", "An", "Bình", "Hồ sơ A", LocalDateTime.of(2026, 12, 31, 10, 30), true, VAULT_ID));
    }

    @Test
    void unlockedNotification_containsLinkToTheVault_withoutDoubleSlash() {
        emailSender.sendVaultUnlockedNotification("an@example.com", "An", "Bình", "Hồ sơ A",
                LocalDateTime.of(2026, 12, 31, 10, 30), true, VAULT_ID);

        String text = sentMessage().getText();
        assertTrue(text.contains("Truy cập hồ sơ: http://localhost:3000/beneficiary/vaults/" + VAULT_ID));
        assertFalse(text.contains("3000//"));
    }

    @Test
    void unlockedNotification_withoutFrontendBaseUrl_hasNoLink() {
        ReflectionTestUtils.setField(emailSender, "frontendBaseUrl", "");

        emailSender.sendVaultUnlockedNotification("an@example.com", "An", "Bình", "Hồ sơ A",
                LocalDateTime.of(2026, 12, 31, 10, 30), true, VAULT_ID);

        assertFalse(sentMessage().getText().contains("Truy cập hồ sơ"));
    }
}
