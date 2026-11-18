package com.ltld.app.legacyvault.service.notificationservice;

import com.ltld.app.legacyvault.utility.EmailSender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ExecutorNotificationServiceImpl implements ExecutorNotificationService {

    private final EmailSender emailSender;

    // Dùng mail sender có sẵn, không gửi OTP hoặc thông tin nhạy cảm.
    @Override
    public void send(String recipientEmail, String content) {
        emailSender.sendHandoverNotice(recipientEmail, content);
    }

    @Override
    public void sendExecutorActivated(String recipientEmail, ExecutorActivatedMail mail) {
        emailSender.sendExecutorActivatedNotice(recipientEmail, mail.executorName(), mail.ownerName(),
                mail.ownerEmail(), mail.type(), mail.waitDays(), mail.acknowledgeBy());
    }
}
