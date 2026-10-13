package com.ltld.app.legacyvault.service.notificationservice;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ExecutorNotificationServiceImpl implements ExecutorNotificationService {

    private final JavaMailSender javaMailSender;

    // Dùng mail sender có sẵn, không gửi OTP hoặc thông tin nhạy cảm.
    @Override
    public void send(String recipientEmail, String content) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(recipientEmail);
        message.setSubject("Thông báo quy trình bàn giao LegacyVault");
        message.setText(content);
        javaMailSender.send(message);
    }
}
