package com.ltld.app.legacyvault.utility;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
public class EmailSender {
    private final JavaMailSender javaMailSender;
    private final int OTP_EXPIRE_SECONDS = 300;
    private static final DateTimeFormatter DMS_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    @Async
    public void sendEmail(String email, String otp) {
        send(email,
                "Mã xác thực OTP - LegacyVault",
                "Mã OTP của bạn là: " + otp);
    }

    @Async
    public void sendPasswordResetEmail(String email, String otp) {
        send(email,
                "Đặt lại mật khẩu - LegacyVault",
                "Mã đặt lại mật khẩu của bạn là: " + otp
                        + "\nNếu bạn không yêu cầu, hãy bỏ qua email này và cân nhắc đổi mật khẩu.");
    }

    /**
     * FR-03: nhắc Owner check-in Dead Man's Switch. Không @Async: người gọi gửi sau khi transaction đã commit
     * và cần bắt được lỗi gửi để ghi log. Không dùng send() vì footer của nó dành cho OTP.
     */
    public void sendDmsCheckinReminder(String email, LocalDateTime checkinDueAt, LocalDateTime triggerAt,
                                       int reminderNumber, int reminderCount) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("Nhắc check-in Dead Man's Switch (" + reminderNumber + "/" + reminderCount + ") - LegacyVault");
        message.setText("Bạn chưa check-in Dead Man's Switch trên LegacyVault."
                + "\nHạn check-in: " + DMS_TIME_FORMAT.format(checkinDueAt)
                + "\nNếu bạn không phản hồi, hệ thống sẽ kích hoạt quy trình bàn giao vào lúc "
                + DMS_TIME_FORMAT.format(triggerAt) + " và gửi thông báo cho Digital Executor của bạn."
                + "\n\nĐể check-in, hãy đăng nhập LegacyVault và chọn \"Check-in\" trong mục Cài đặt Dead Man's Switch."
                + "\nĐây là lần nhắc thứ " + reminderNumber + " trên tổng số " + reminderCount + " lần.");
        javaMailSender.send(message);
    }

    private void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body
                + "\nMã có hiệu lực trong " + (OTP_EXPIRE_SECONDS / 60) + " phút."
                + "\nVui lòng không chia sẻ mã này với bất kỳ ai.");
        javaMailSender.send(message);
    }
}
