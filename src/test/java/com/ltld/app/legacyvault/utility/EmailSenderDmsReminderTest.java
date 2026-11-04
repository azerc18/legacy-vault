package com.ltld.app.legacyvault.utility;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/** FR-03 (m1): mail nhắc check-in DMS hiển thị giờ Việt Nam, không kèm footer OTP. */
@ExtendWith(MockitoExtension.class)
class EmailSenderDmsReminderTest {

    @Mock private JavaMailSender javaMailSender;
    @InjectMocks private EmailSender emailSender;

    private TimeZone originalZone;

    @BeforeEach
    void setUp() {
        // Máy chủ chạy UTC như container Docker.
        originalZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
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
    @DisplayName("m1 (T4): 02:00 UTC hiển thị 09:00 (giờ Việt Nam) cho cả hạn check-in và hạn kích hoạt")
    void checkinReminder_showsVietnamTime() {
        emailSender.sendDmsCheckinReminder("owner@example.com",
                LocalDateTime.of(2026, 11, 1, 2, 0), LocalDateTime.of(2026, 11, 8, 2, 0), 1, 3);

        SimpleMailMessage message = sentMessage();
        String text = message.getText();
        assertThat(message.getTo()).containsExactly("owner@example.com");
        assertThat(message.getSubject()).contains("(1/3)");
        assertThat(text).contains("Check-in deadline: 01/11/2026 09:00 (Vietnam time)");
        assertThat(text).contains("handover process at 08/11/2026 09:00 (Vietnam time)");
        assertThat(text).doesNotContain("02:00");
        assertThat(text).doesNotContain("The code is valid for");
    }

    @Test
    @DisplayName("M1: lỗi gửi được ném ra để người gọi trả lại lần nhắc")
    void checkinReminder_propagatesSendFailure() {
        doThrow(new MailSendException("smtp down")).when(javaMailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> emailSender.sendDmsCheckinReminder("owner@example.com",
                LocalDateTime.of(2026, 11, 1, 2, 0), LocalDateTime.of(2026, 11, 8, 2, 0), 1, 3))
                .isInstanceOf(MailSendException.class);
    }

    @Test
    @DisplayName("F2: mail nhắc kèm link tới trang FE /dms/checkin (không phải API), token được URL-encode")
    void checkinReminder_withToken_linksToFrontendConfirmPage() {
        ReflectionTestUtils.setField(emailSender, "frontendBaseUrl", "https://legacyvault.test/");

        emailSender.sendDmsCheckinReminder("owner@example.com",
                LocalDateTime.of(2026, 11, 1, 2, 0), LocalDateTime.of(2026, 11, 8, 2, 0), 1, 3, "a.b+c/d");

        String text = sentMessage().getText();
        assertThat(text).contains("https://legacyvault.test/dms/checkin?token=a.b%2Bc%2Fd");
        assertThat(text).doesNotContain("/api/");
    }

    @Test
    @DisplayName("F2: không có token hoặc chưa cấu hình FRONTEND_BASE_URL -> mail như cũ, không kèm link")
    void checkinReminder_withoutTokenOrBase_hasNoLink() {
        emailSender.sendDmsCheckinReminder("owner@example.com",
                LocalDateTime.of(2026, 11, 1, 2, 0), LocalDateTime.of(2026, 11, 8, 2, 0), 1, 3, "token");

        assertThat(sentMessage().getText()).doesNotContain("/dms/checkin");
    }
}
