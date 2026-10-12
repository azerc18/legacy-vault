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
    public void sendEmail(String email, String otp) {
        send(email,
                "Mã xác thực OTP - LegacyVault",
                "Mã OTP của bạn là: " + otp + otpFooter());
    }

    @Async
    public void sendPasswordResetEmail(String email, String otp) {
        send(email,
                "Đặt lại mật khẩu - LegacyVault",
                "Mã đặt lại mật khẩu của bạn là: " + otp
                        + "\nNếu bạn không yêu cầu, hãy bỏ qua email này và cân nhắc đổi mật khẩu."
                        + otpFooter());
    }

    // FR-02: mời người thụ hưởng chưa có tài khoản (hoặc chưa xác minh email) đăng ký
    @Async
    public void sendBeneficiaryInvitation(String email, String ownerName, String vaultName) {
        send(email,
                "Lời mời trở thành người thụ hưởng - LegacyVault",
                ownerName + " đã chỉ định bạn là người thụ hưởng của Vault \"" + vaultName + "\" trên LegacyVault."
                        + "\nVui lòng đăng ký tài khoản (hoặc hoàn tất xác minh email nếu đã đăng ký) bằng chính địa chỉ email này"
                        + " để được liên kết với Vault.");
    }

    // FR-02: thông báo cho người thụ hưởng đã có tài khoản
    @Async
    public void sendBeneficiaryAssignedNotice(String email, String ownerName, String vaultName) {
        send(email,
                "Bạn được chỉ định là người thụ hưởng - LegacyVault",
                ownerName + " đã chỉ định bạn là người thụ hưởng của Vault \"" + vaultName + "\" trên LegacyVault."
                        + "\nBạn sẽ nhận được thông báo khi Vault được mở khóa để bàn giao.");
    }

    // FR-02 (bước 7): xác nhận cho Owner
    @Async
    public void sendBeneficiaryAssignmentConfirmation(String ownerEmail, String vaultName,
                                                      String beneficiaryEmail, boolean pendingRegistration) {
        send(ownerEmail,
                "Xác nhận chỉ định người thụ hưởng - LegacyVault",
                "Bạn đã chỉ định " + beneficiaryEmail + " là người thụ hưởng của Vault \"" + vaultName + "\"."
                        + (pendingRegistration
                        ? "\nNgười này chưa có tài khoản đã xác minh; hệ thống đã gửi lời mời đăng ký."
                        : ""));
    }

    private String otpFooter() {
        return "\nMã có hiệu lực trong " + (OTP_EXPIRE_SECONDS / 60) + " phút."
                + "\nVui lòng không chia sẻ mã này với bất kỳ ai.";
    }

    private void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        javaMailSender.send(message);
    }
}
