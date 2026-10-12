package com.ltld.app.legacyvault.utility;

import com.ltld.app.legacyvault.enums.ExecutorType;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmailSender {
    private final JavaMailSender javaMailSender;
    private final int OTP_EXPIRE_SECONDS = 300;

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

    // FR-06: gửi đồng bộ (gọi từ listener AFTER_COMMIT, lỗi do listener bắt và log).
    // Không nêu tên Executor còn lại của Owner.
    public void sendExecutorAssignedNotice(String to, String ownerName, String ownerEmail, ExecutorType type) {
        String role = type == ExecutorType.PRIMARY ? "Executor chính (PRIMARY)" : "Executor dự phòng (BACKUP)";
        sendWithoutOtpFooter(to,
                "Bạn được chỉ định làm Digital Executor - LegacyVault",
                ownerName + " (" + ownerEmail + ") đã chỉ định bạn làm " + role + " trên LegacyVault."
                        + "\nBạn chưa cần làm gì lúc này. Khi Dead Man's Switch của " + ownerName
                        + " được kích hoạt, hệ thống sẽ gửi thông báo để bạn đăng nhập và xác nhận tiếp nhận quy trình bàn giao."
                        + "\nNếu bạn không biết người này, hãy liên hệ bộ phận hỗ trợ LegacyVault.");
    }

    public void sendExecutorRevokedNotice(String to, String ownerName, String ownerEmail, ExecutorType type) {
        String role = type == ExecutorType.PRIMARY ? "Executor chính (PRIMARY)" : "Executor dự phòng (BACKUP)";
        sendWithoutOtpFooter(to,
                "Quyền Digital Executor của bạn đã bị thu hồi - LegacyVault",
                ownerName + " (" + ownerEmail + ") đã thu hồi vai trò " + role + " của bạn trên LegacyVault."
                        + "\nTừ thời điểm này bạn không còn quyền thực hiện quy trình bàn giao cho " + ownerName + ".");
    }

    private void sendWithoutOtpFooter(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
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
