package com.ltld.app.legacyvault.utility;

import com.ltld.app.legacyvault.enums.AppLanguage;
import com.ltld.app.legacyvault.service.userservice.UserLanguageResolver;
import jakarta.mail.internet.MimeMessage;
import com.ltld.app.legacyvault.enums.ExecutorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailSenderExecutorNoticeTest {

    @Mock private JavaMailSender javaMailSender;
    @Mock private UserLanguageResolver languageResolver;

    @BeforeEach
    void stubMail() {
        SentMail.stub(javaMailSender, languageResolver, AppLanguage.EN);
    }

    @InjectMocks
    private EmailSender emailSender;

    private static final java.util.UUID ASSIGNMENT_ID =
            java.util.UUID.fromString("11111111-2222-3333-4444-555555555555");

    // Lời mời: có app.frontend.base-url thì có nút dẫn tới trang đồng ý / từ chối của đúng assignment
    @Test
    void assignedNotice_withFrontendUrl_linksToInvitationPage() {
        org.springframework.test.util.ReflectionTestUtils.setField(emailSender, "frontendBaseUrl", "https://app.example.test/");

        emailSender.sendExecutorAssignedNotice("exec@example.test", "Owner A", "owner@example.test",
                ExecutorType.PRIMARY, ASSIGNMENT_ID);

        assertThat(sentMessage().getText())
                .contains("View and respond to the invitation: https://app.example.test/executor/invitations/" + ASSIGNMENT_ID)
                .contains("only become the executor after you accept");
    }

    // Chưa cấu hình frontend: không có link, hướng dẫn đăng nhập thay thế
    @Test
    void assignedNotice_withoutFrontendUrl_explainsHowToRespond() {
        emailSender.sendExecutorAssignedNotice("exec@example.test", "Owner A", "owner@example.test",
                ExecutorType.PRIMARY, ASSIGNMENT_ID);

        assertThat(sentMessage().getText())
                .contains("Sign in to LegacyVault and open the Executor page")
                .doesNotContain("/executor/invitations/");
    }

    @Test
    void invitationAccepted_tellsOwner() {
        emailSender.sendExecutorInvitationResponseNotice("owner@example.test", "Exec B (exec@example.test)",
                ExecutorType.PRIMARY, true, null);

        SentMail message = sentMessage();
        assertThat(message.getTo()).containsExactly("owner@example.test");
        assertThat(message.getText()).contains("Exec B (exec@example.test) has accepted to be your Primary executor")
                .doesNotContain("Reason:");
    }

    @Test
    void invitationDeclined_tellsOwnerWithReason_andLinksToExecutorsPage() {
        org.springframework.test.util.ReflectionTestUtils.setField(emailSender, "frontendBaseUrl", "https://app.example.test");

        emailSender.sendExecutorInvitationResponseNotice("owner@example.test", "Exec B (exec@example.test)",
                ExecutorType.BACKUP, false, "  Too busy  ");

        assertThat(sentMessage().getText())
                .contains("Exec B (exec@example.test) has declined to be your Backup executor",
                        "Reason: Too busy", "https://app.example.test/owner/executors");
    }

    @Test
    void assignedNotice_mentionsOwnerAndRole_withoutOtpFooter() {
        emailSender.sendExecutorAssignedNotice("exec@example.test", "Owner A", "owner@example.test",
                ExecutorType.BACKUP, ASSIGNMENT_ID);

        SentMail message = sentMessage();
        assertThat(message.getTo()).containsExactly("exec@example.test");
        assertThat(message.getText())
                .contains("Owner A", "owner@example.test", "BACKUP", "Dead Man's Switch")
                .doesNotContain("OTP", "The code is valid for");
    }

    @Test
    void revokedNotice_mentionsOwner_withoutOtpFooter() {
        emailSender.sendExecutorRevokedNotice("old@example.test", "Owner A", "owner@example.test",
                ExecutorType.PRIMARY);

        SentMail message = sentMessage();
        assertThat(message.getTo()).containsExactly("old@example.test");
        assertThat(message.getSubject()).contains("revoked");
        assertThat(message.getText())
                .contains("Owner A", "owner@example.test", "PRIMARY")
                .doesNotContain("OTP", "The code is valid for");
    }

    // FR-07 / BR-03: hệ thống tự thu hồi, không được nói Owner đã thu hồi (Owner thường đã mất)
    @Test
    void timeoutRevokedNotice_saysSystemNotOwner_withWaitDays() {
        emailSender.sendExecutorTimeoutRevokedNotice("old@example.test", "Owner A", "owner@example.test", 14);

        SentMail message = sentMessage();
        assertThat(message.getTo()).containsExactly("old@example.test");
        assertThat(message.getText())
                .contains("Owner A", "owner@example.test", "14 days", "backup executor")
                .doesNotContain("has revoked", "OTP", "The code is valid for");
    }

    @Test
    void replacedByAdminNotice_saysAdministratorAssignedNewExecutor() {
        emailSender.sendExecutorReplacedByAdminNotice("old@example.test", "Owner A", "owner@example.test",
                ExecutorType.BACKUP);

        SentMail message = sentMessage();
        assertThat(message.getTo()).containsExactly("old@example.test");
        assertThat(message.getText())
                .contains("Owner A", "owner@example.test", "BACKUP", "administrator",
                        "assigned a new", "continue the handover process")
                .doesNotContain("has revoked", "OTP", "The code is valid for", "not started", "days");
    }

    // T5: Owner không có fullName -> dùng email làm tên hiển thị, không in chữ "null".
    @Test
    void assignedNotice_ownerWithoutName_usesEmailAndNeverPrintsNull() {
        emailSender.sendExecutorAssignedNotice("exec@example.test", null, "owner@example.test", ExecutorType.PRIMARY,
                ASSIGNMENT_ID);

        assertThat(sentMessage().getText())
                .contains("owner@example.test would like to appoint you", "Dead Man's Switch of owner@example.test")
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
        emailSender.sendExecutorAssignedNotice("exec@example.test", "Owner A", "owner@example.test", ExecutorType.PRIMARY,
                ASSIGNMENT_ID);

        assertThat(sentMessage().getText())
                .contains("Owner A (owner@example.test) would like to appoint you", "Dead Man's Switch of Owner A ")
                .doesNotContain("null");
    }

    @Test
    void revokedNotice_ownerWithName_showsNameAndEmail() {
        emailSender.sendExecutorRevokedNotice("old@example.test", "Owner A", "owner@example.test", ExecutorType.BACKUP);

        assertThat(sentMessage().getText())
                .contains("Owner A (owner@example.test) has revoked", "handover process for Owner A.")
                .doesNotContain("null");
    }

    private SentMail sentMessage() {
        return SentMail.captureOne(javaMailSender);
    }
}
