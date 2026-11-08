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

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Mail ưu tiên có bản Việt và Anh theo preferredLanguage của người nhận (D.5), kèm HTML và bản chữ trơn. */
@ExtendWith(MockitoExtension.class)
class EmailSenderLanguageTest {

    @Mock private JavaMailSender javaMailSender;
    @Mock private UserLanguageResolver languageResolver;
    @InjectMocks private EmailSender emailSender;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailSender, "frontendBaseUrl", "https://legacyvault.test");
    }

    @Test
    void registrationOtp_vietnamese() {
        SentMail.stub(javaMailSender, languageResolver, AppLanguage.VI);

        emailSender.sendEmail("an@example.com", "123456");

        SentMail mail = SentMail.captureOne(javaMailSender);
        assertThat(mail.getSubject()).isEqualTo("Mã xác minh OTP - LegacyVault");
        assertThat(mail.getText()).contains("123456", "Mã có hiệu lực trong 5 phút");
        assertThat(mail.getHtml()).contains("123456", "<table");
    }

    @Test
    void registrationOtp_english() {
        SentMail.stub(javaMailSender, languageResolver, AppLanguage.EN);

        emailSender.sendEmail("an@example.com", "123456");

        SentMail mail = SentMail.captureOne(javaMailSender);
        assertThat(mail.getSubject()).isEqualTo("OTP verification code - LegacyVault");
        assertThat(mail.getText()).contains("123456", "The code is valid for 5 minutes");
    }

    @Test
    void passwordReset_followsLanguage() {
        SentMail.stub(javaMailSender, languageResolver, AppLanguage.VI);

        emailSender.sendPasswordResetEmail("an@example.com", "654321");

        SentMail mail = SentMail.captureOne(javaMailSender);
        assertThat(mail.getSubject()).isEqualTo("Đặt lại mật khẩu - LegacyVault");
        assertThat(mail.getText()).contains("654321", "bỏ qua thư này");
    }

    @Test
    void vaultUnlocked_vietnameseHasVietnameseDeadlineAndLink() {
        SentMail.stub(javaMailSender, languageResolver, AppLanguage.VI);
        UUID vaultId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        emailSender.sendVaultUnlockedNotification("an@example.com", "An", "Bình", "Hồ sơ A",
                LocalDateTime.of(2026, 12, 31, 10, 30), false, vaultId);

        SentMail mail = SentMail.captureOne(javaMailSender);
        assertThat(mail.getSubject()).isEqualTo("Tài sản số đã được bàn giao cho bạn - LegacyVault");
        assertThat(mail.getText()).contains("Két sắt \"Hồ sơ A\" của Bình", "Hạn nhận:",
                "Tài khoản của bạn chưa được kích hoạt",
                "https://legacyvault.test/beneficiary/vaults/" + vaultId);
        assertThat(mail.getHtml()).contains("href=\"https://legacyvault.test/beneficiary/vaults/" + vaultId + "\"");
    }

    @Test
    void messageMissingInEnglish_fallsBackToVietnameseInsteadOfFailing() {
        assertThat(MailMessages.get(AppLanguage.EN, "mail.only.in.vi.test")).isEqualTo("mail.only.in.vi.test");
        assertThat(MailMessages.has(AppLanguage.EN, "mail.otp.subject")).isTrue();
    }

    @Test
    void sender_usesConfiguredFromAddress() {
        SentMail.stub(javaMailSender, languageResolver, AppLanguage.EN);
        ReflectionTestUtils.setField(emailSender, "mailFrom", "LegacyVault <no-reply@legacyvault.test>");

        emailSender.sendEmail("an@example.com", "123456");

        assertThat(SentMail.captureOne(javaMailSender).getFrom()).contains("no-reply@legacyvault.test");
    }

    @Test
    void htmlEscapesUserSuppliedText() {
        SentMail.stub(javaMailSender, languageResolver, AppLanguage.EN);

        emailSender.sendBeneficiaryRemovedNotice("ben@example.com", "<b>Owner</b>", "Vault");

        assertThat(SentMail.captureOne(javaMailSender).getHtml()).doesNotContain("<b>Owner</b>").contains("&lt;b&gt;");
    }

    @Test
    void welcome_vietnameseAndEnglish() {
        SentMail.stub(javaMailSender, languageResolver, AppLanguage.VI);
        emailSender.sendWelcomeEmail("an@example.com", "An");
        SentMail vi = SentMail.captureOne(javaMailSender);
        assertThat(vi.getSubject()).isEqualTo("Chào mừng đến với LegacyVault");
        assertThat(vi.getText()).contains("Xin chào An,", "https://legacyvault.test/login");
    }

    @Test
    void welcome_english_fallsBackToEmailWhenNoName() {
        SentMail.stub(javaMailSender, languageResolver, AppLanguage.EN);
        emailSender.sendWelcomeEmail("an@example.com", null);
        SentMail en = SentMail.captureOne(javaMailSender);
        assertThat(en.getSubject()).isEqualTo("Welcome to LegacyVault");
        assertThat(en.getText()).contains("Hello an@example.com,");
    }

    @Test
    void passwordChanged_warnsAndLinksToReset() {
        SentMail.stub(javaMailSender, languageResolver, AppLanguage.EN);
        emailSender.sendPasswordChangedNotice("an@example.com", "An");
        SentMail mail = SentMail.captureOne(javaMailSender);
        assertThat(mail.getSubject()).isEqualTo("Your password was changed - LegacyVault");
        assertThat(mail.getText()).contains("If this was not you", "https://legacyvault.test/forgot-password");
    }

    @Test
    void accountLocked_mentionsMinutes() {
        SentMail.stub(javaMailSender, languageResolver, AppLanguage.VI);
        emailSender.sendAccountLockedNotice("an@example.com", "An", 15);
        SentMail mail = SentMail.captureOne(javaMailSender);
        assertThat(mail.getSubject()).isEqualTo("Tài khoản tạm thời bị khóa - LegacyVault");
        assertThat(mail.getText()).contains("khóa 15 phút");
    }

    @Test
    void dmsCheckinConfirmation_showsNextDeadlineInVietnamTime() {
        SentMail.stub(javaMailSender, languageResolver, AppLanguage.EN);
        java.util.TimeZone original = java.util.TimeZone.getDefault();
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
        try {
            emailSender.sendDmsCheckinConfirmation("an@example.com", "An", LocalDateTime.of(2026, 12, 1, 10, 30));
        } finally {
            java.util.TimeZone.setDefault(original);
        }
        SentMail mail = SentMail.captureOne(javaMailSender);
        assertThat(mail.getText()).contains("01/12/2026 17:30 (GMT+7)", "/owner/dms");
    }
}
