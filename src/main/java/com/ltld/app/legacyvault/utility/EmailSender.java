package com.ltld.app.legacyvault.utility;

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
    public void sendEmail(String email, String otp){
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("Mã xác thực OTP - LegacyVault");
        message.setText("Mã OTP của bạn là: " + otp
                + "\nMã có hiệu lực trong " + (OTP_EXPIRE_SECONDS / 60) + " phút."
                + "\nVui lòng không chia sẻ mã này với bất kỳ ai.");
        javaMailSender.send(message);
    }

}
