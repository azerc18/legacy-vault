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

    // Lý do từ chối do người dùng gõ: không được biến thành nút bấm trong mail của Owner
    // (đoạn riêng dạng "Nhãn: https://..." sẽ bị MailHtml dựng thành nút).
    @Test
    void invitationDeclined_reasonWithUrl_isNotRenderedAsButton() {
        emailSender.sendExecutorInvitationResponseNotice("owner@example.test", "Exec B (exec@example.test)",
                ExecutorType.PRIMARY, false, "https://phishing.example.test");

        SentMail message = sentMessage();
        assertThat(message.getText()).contains("Reason: https://phishing.example.test");
        // Nút dùng display:inline-block + padding:13px 44px; link thường trong đoạn thì không.
        assertThat(message.getHtml()).doesNotContain("display:inline-block;background:");
    }

    // Hạn lời mời: nhắc người được mời, có link tới đúng lời mời
    @Test
    void invitationReminder_tellsDaysLeftAndLinksToInvitation() {
        org.springframework.test.util.ReflectionTestUtils.setField(emailSender, "frontendBaseUrl", "https://app.example.test");

        emailSender.sendExecutorInvitationReminder("exec@example.test", "Owner A", "owner@example.test",
                ExecutorType.PRIMARY, ASSIGNMENT_ID, 3);

        SentMail message = sentMessage();
        assertThat(message.getTo()).containsExactly("exec@example.test");
        assertThat(message.getText())
                .contains("3 day(s) left", "Owner A", "https://app.example.test/executor/invitations/" + ASSIGNMENT_ID);
    }

    // Hạn lời mời: báo Owner khi đã hết hạn, dẫn về trang Người thực thi
    @Test
    void invitationExpired_tellsOwnerPositionIsEmpty() {
        org.springframework.test.util.ReflectionTestUtils.setField(emailSender, "frontendBaseUrl", "https://app.example.test");

        emailSender.sendExecutorInvitationExpiredNotice("owner@example.test", "Exec B (exec@example.test)",
                ExecutorType.BACKUP, 7);

        SentMail message = sentMessage();
        assertThat(message.getTo()).containsExactly("owner@example.test");
        assertThat(message.getText())
                .contains("Exec B (exec@example.test)", "7 day(s)", "https://app.example.test/owner/executors");
    }

    // Hạn lời mời: báo người được mời rằng lời mời đã hết hiệu lực
    @Test
    void invitationExpired_tellsInvitee() {
        emailSender.sendExecutorInvitationExpiredToInvitee("exec@example.test", "Owner A", "owner@example.test",
                ExecutorType.PRIMARY, 7);

        SentMail message = sentMessage();
        assertThat(message.getTo()).containsExactly("exec@example.test");
        assertThat(message.getText()).contains("Owner A", "7 day(s)", "has expired", "ask");
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

    // FR-07: mail kích hoạt cho Primary: tiêu đề có tên Owner, hạn xác nhận theo giờ VN, nút mở trang Người thực thi,
    // không còn dán mã assignment thô
    @Test
    void activatedNotice_primary_hasDeadlineAndButtonWithoutRawCode() {
        org.springframework.test.util.ReflectionTestUtils.setField(emailSender, "frontendBaseUrl", "https://app.example.test/");
        java.time.LocalDateTime ackBy = java.time.LocalDateTime.of(2026, 12, 2, 9, 30);

        emailSender.sendExecutorActivatedNotice("exec@example.test", "Exec B", "Owner A", "owner@example.test",
                ExecutorType.PRIMARY, 14, ackBy);

        SentMail message = sentMessage();
        assertThat(message.getSubject()).isEqualTo("Handover started for Owner A - LegacyVault");
        assertThat(message.getText())
                .contains("Hello Exec B,")
                .contains("The Dead Man's Switch of Owner A (owner@example.test) has been triggered")
                .contains("You are their Primary executor (PRIMARY)")
                .contains("within 14 days, before " + EmailSender.formatDeadline(ackBy) + " (Vietnam time)")
                .contains("Open the Executor page: https://app.example.test/executor/home")
                .doesNotContain("code:")
                .doesNotContain("null");
        assertThat(message.getHtml()).contains("href=\"https://app.example.test/executor/home\"");
    }

    // Backup: không có hạn (BR-03 chỉ leo thang Primary), giải thích vì sao Backup nhận việc
    @Test
    void activatedNotice_backup_hasNoDeadline() {
        emailSender.sendExecutorActivatedNotice("exec@example.test", null, null, "owner@example.test",
                ExecutorType.BACKUP, null, null);

        SentMail message = sentMessage();
        assertThat(message.getSubject()).isEqualTo("Handover started for owner@example.test - LegacyVault");
        assertThat(message.getText())
                .contains("Hello exec@example.test,")
                .contains("You are their Backup executor (BACKUP)")
                .contains("primary executor is not available")
                .contains("Sign in to LegacyVault and open the Executor page to acknowledge this notice.")
                .doesNotContain("Vietnam time")
                .doesNotContain("null");
    }

    // Ngôn ngữ theo người nhận (email language preference)
    @Test
    void activatedNotice_vietnameseRecipient_getsVietnameseMail() {
        SentMail.stub(javaMailSender, languageResolver, AppLanguage.VI);

        emailSender.sendExecutorActivatedNotice("exec@example.test", "Exec B", "Owner A", "owner@example.test",
                ExecutorType.PRIMARY, 14, java.time.LocalDateTime.of(2026, 12, 2, 9, 30));

        SentMail message = sentMessage();
        assertThat(message.getSubject()).isEqualTo("Bắt đầu bàn giao di sản của Owner A - LegacyVault");
        assertThat(message.getText())
                .contains("Xin chào Exec B,")
                .contains("Công tắc người chết của Owner A (owner@example.test) đã kích hoạt")
                .contains("Người thực thi chính (PRIMARY)")
                .contains("trong 14 ngày")
                .contains("(giờ Việt Nam)");
    }

    // Các mail bàn giao còn lại (câu tiếng Anh dựng sẵn) được bọc vào cùng bố cục HTML, mỗi dòng một đoạn
    @Test
    void handoverNotice_wrapsPlainContentInSharedLayout() {
        emailSender.sendHandoverNotice("exec@example.test", "Line one.\nLine two.");

        SentMail message = sentMessage();
        assertThat(message.getSubject()).isEqualTo("LegacyVault handover process notification");
        assertThat(message.getHtml()).contains("Line one.", "Line two.", "Handover process notification");
        assertThat(message.getText()).contains("Line one.", "Line two.");
    }

    private SentMail sentMessage() {
        return SentMail.captureOne(javaMailSender);
    }
}
