package com.ltld.app.legacyvault.utility;

import com.ltld.app.legacyvault.enums.ExecutorType;
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

@ExtendWith(MockitoExtension.class)
class EmailSenderExecutorNoticeTest {

    @Mock private JavaMailSender javaMailSender;

    @InjectMocks
    private EmailSender emailSender;

    @Test
    void assignedNotice_mentionsOwnerAndRole_withoutOtpFooter() {
        emailSender.sendExecutorAssignedNotice("exec@example.test", "Owner A", "owner@example.test",
                ExecutorType.BACKUP);

        SimpleMailMessage message = sentMessage();
        assertThat(message.getTo()).containsExactly("exec@example.test");
        assertThat(message.getText())
                .contains("Owner A", "owner@example.test", "BACKUP", "Dead Man's Switch")
                .doesNotContain("OTP", "Mã có hiệu lực");
    }

    @Test
    void revokedNotice_mentionsOwner_withoutOtpFooter() {
        emailSender.sendExecutorRevokedNotice("old@example.test", "Owner A", "owner@example.test",
                ExecutorType.PRIMARY);

        SimpleMailMessage message = sentMessage();
        assertThat(message.getTo()).containsExactly("old@example.test");
        assertThat(message.getSubject()).contains("thu hồi");
        assertThat(message.getText())
                .contains("Owner A", "owner@example.test", "PRIMARY")
                .doesNotContain("OTP", "Mã có hiệu lực");
    }

    private SimpleMailMessage sentMessage() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender).send(captor.capture());
        return captor.getValue();
    }
}
