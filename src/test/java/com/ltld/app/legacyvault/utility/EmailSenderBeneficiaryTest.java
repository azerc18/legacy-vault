package com.ltld.app.legacyvault.utility;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/** FR-02 (M2): email OTP vẫn có footer OTP; ba email người thụ hưởng không có. */
@ExtendWith(MockitoExtension.class)
class EmailSenderBeneficiaryTest {

    @Mock private JavaMailSender javaMailSender;
    @InjectMocks private EmailSender emailSender;

    private SimpleMailMessage sentMessage() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender).send(captor.capture());
        return captor.getValue();
    }

    private static void assertNoOtpFooter(SimpleMailMessage message) {
        assertThat(message.getText()).doesNotContain("Mã có hiệu lực");
        assertThat(message.getText()).doesNotContain("không chia sẻ mã");
    }

    @Test
    @DisplayName("M2 (T3): email OTP xác minh vẫn có footer OTP")
    void otpEmail_keepsOtpFooter() {
        emailSender.sendEmail("user@example.com", "123456");

        SimpleMailMessage message = sentMessage();
        assertThat(message.getTo()).containsExactly("user@example.com");
        assertThat(message.getText()).contains("123456");
        assertThat(message.getText()).contains("Mã có hiệu lực trong 5 phút.");
        assertThat(message.getText()).contains("Vui lòng không chia sẻ mã này");
    }

    @Test
    @DisplayName("M2 (T3): email đặt lại mật khẩu vẫn có footer OTP")
    void passwordResetEmail_keepsOtpFooter() {
        emailSender.sendPasswordResetEmail("user@example.com", "654321");

        SimpleMailMessage message = sentMessage();
        assertThat(message.getTo()).containsExactly("user@example.com");
        assertThat(message.getText()).contains("654321");
        assertThat(message.getText()).contains("Vui lòng không chia sẻ mã này");
    }

    @Test
    @DisplayName("M2 (T3): lời mời người thụ hưởng không có footer OTP")
    void beneficiaryInvitation_hasNoOtpFooter() {
        emailSender.sendBeneficiaryInvitation("ben@example.com", "Owner", "My Vault");

        SimpleMailMessage message = sentMessage();
        assertThat(message.getTo()).containsExactly("ben@example.com");
        assertThat(message.getSubject()).isEqualTo("Lời mời trở thành người thụ hưởng - LegacyVault");
        assertThat(message.getText()).contains("Owner").contains("\"My Vault\"");
        assertNoOtpFooter(message);
    }

    @Test
    @DisplayName("M2 (T3): thông báo người thụ hưởng đã có tài khoản không có footer OTP")
    void beneficiaryAssignedNotice_hasNoOtpFooter() {
        emailSender.sendBeneficiaryAssignedNotice("ben@example.com", "Owner", "My Vault");

        SimpleMailMessage message = sentMessage();
        assertThat(message.getTo()).containsExactly("ben@example.com");
        assertThat(message.getSubject()).isEqualTo("Bạn được chỉ định là người thụ hưởng - LegacyVault");
        assertNoOtpFooter(message);
    }

    @Test
    @DisplayName("M2 (T3): xác nhận cho Owner không có footer OTP")
    void beneficiaryAssignmentConfirmation_hasNoOtpFooter() {
        emailSender.sendBeneficiaryAssignmentConfirmation("owner@example.com", "My Vault", "ben@example.com", true);

        SimpleMailMessage message = sentMessage();
        assertThat(message.getTo()).containsExactly("owner@example.com");
        assertThat(message.getSubject()).isEqualTo("Xác nhận chỉ định người thụ hưởng - LegacyVault");
        assertThat(message.getText()).contains("ben@example.com").contains("đã gửi lời mời đăng ký");
        assertNoOtpFooter(message);
    }
}
