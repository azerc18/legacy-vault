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
                .doesNotContain("OTP", "The code is valid for");
    }

    @Test
    void revokedNotice_mentionsOwner_withoutOtpFooter() {
        emailSender.sendExecutorRevokedNotice("old@example.test", "Owner A", "owner@example.test",
                ExecutorType.PRIMARY);

        SimpleMailMessage message = sentMessage();
        assertThat(message.getTo()).containsExactly("old@example.test");
        assertThat(message.getSubject()).contains("revoked");
        assertThat(message.getText())
                .contains("Owner A", "owner@example.test", "PRIMARY")
                .doesNotContain("OTP", "The code is valid for");
    }

    // T5: Owner không có fullName -> dùng email làm tên hiển thị, không in chữ "null".
    @Test
    void assignedNotice_ownerWithoutName_usesEmailAndNeverPrintsNull() {
        emailSender.sendExecutorAssignedNotice("exec@example.test", null, "owner@example.test", ExecutorType.PRIMARY);

        assertThat(sentMessage().getText())
                .contains("owner@example.test has assigned you", "Dead Man's Switch of owner@example.test")
                .doesNotContain("null");
    }

    @Test
    void revokedNotice_ownerWithoutName_usesEmailAndNeverPrintsNull() {
        emailSender.sendExecutorRevokedNotice("old@example.test", "  ", "owner@example.test", ExecutorType.BACKUP);

        assertThat(sentMessage().getText())
                .contains("owner@example.test has revoked", "handover process for owner@example.test.")
                .doesNotContain("null", "()");
    }

    @Test
    void assignedNotice_ownerWithName_showsNameAndEmail() {
        emailSender.sendExecutorAssignedNotice("exec@example.test", "Owner A", "owner@example.test", ExecutorType.PRIMARY);

        assertThat(sentMessage().getText())
                .contains("Owner A (owner@example.test) has assigned you", "Dead Man's Switch of Owner A ")
                .doesNotContain("null");
    }

    @Test
    void revokedNotice_ownerWithName_showsNameAndEmail() {
        emailSender.sendExecutorRevokedNotice("old@example.test", "Owner A", "owner@example.test", ExecutorType.BACKUP);

        assertThat(sentMessage().getText())
                .contains("Owner A (owner@example.test) has revoked", "handover process for Owner A.")
                .doesNotContain("null");
    }

    private SimpleMailMessage sentMessage() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender).send(captor.capture());
        return captor.getValue();
    }
}
